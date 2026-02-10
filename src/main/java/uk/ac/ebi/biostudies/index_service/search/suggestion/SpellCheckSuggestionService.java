package uk.ac.ebi.biostudies.index_service.search.suggestion;

import java.io.IOException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.spell.SuggestMode;
import org.apache.lucene.search.spell.SuggestWord;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;

/**
 * Provides intelligent spelling suggestions for search queries using a cascading strategy across
 * multiple indexes.
 *
 * <p>This service uses Lucene's {@link DirectSpellChecker} to suggest corrections for misspelled
 * terms by searching across different indexes in a prioritized order:
 *
 * <ol>
 *   <li><strong>Accession patterns:</strong> Queries matching accession formats (e.g., "S-BSST143")
 *       are first checked against the submission accession field
 *   <li><strong>EFO ontology terms:</strong> Biological/ontology terms (e.g., "osteoclastt") are
 *       checked against the EFO term index
 *   <li><strong>General content:</strong> Falls back to submission content for other terms (author
 *       names, descriptions, etc.)
 * </ol>
 *
 * <p><strong>Examples:</strong>
 *
 * <ul>
 *   <li>"osteoclastt" → "osteoclast" (from EFO index)
 *   <li>"S-BSST143" → "S-BSST1432" (from submission accession field)
 *   <li>"melanogaste" → "melanogaster" (from submission content)
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This service is thread-safe. It uses {@link IndexManager} for
 * searcher acquisition/release which handles concurrent access properly.
 *
 * <p><strong>Dependencies:</strong>
 *
 * <ul>
 *   <li>Requires {@link DirectSpellChecker} to be initialized via {@link
 *       uk.ac.ebi.biostudies.index_service.index.management.init.SpellCheckerInitializer}
 *   <li>Requires EFO and SUBMISSION indexes to be open
 * </ul>
 *
 * @see DirectSpellChecker
 * @see IndexManager
 */
@Slf4j
@Service
public class SpellCheckSuggestionService {

  private final IndexManager indexManager;
  /**
   * -- SETTER --
   *  Sets the spell checker instance. Called by
   *  during
   *  startup.
   *
   * @param spellChecker the configured DirectSpellChecker instance
   */
  @Setter
  private DirectSpellChecker spellChecker;

  /**
   * Constructs the suggestion service.
   *
   * @param indexManager manager for acquiring/releasing index searchers
   */
  public SpellCheckSuggestionService(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  /**
   * Checks if the spell checker has been initialized and is ready for use.
   *
   * @return true if spell checker is available
   */
  public boolean isAvailable() {
    return spellChecker != null;
  }

  /**
   * Suggests similar terms for the given query using intelligent index selection.
   *
   * <p>The suggestion strategy depends on the query pattern:
   *
   * <ul>
   *   <li><strong>Accession-like queries</strong> (e.g., "S-BSST143", "E-MTAB-123"): Checks the
   *       accession field first, then falls back to EFO and content
   *   <li><strong>Other queries:</strong> Checks EFO ontology terms first, then falls back to
   *       submission content
   * </ul>
   *
   * @param queryString the query string to get suggestions for (must not be null or blank)
   * @param maxSuggestions maximum number of suggestions to return (typically 5)
   * @return array of suggested spellings ordered by relevance, empty array if no suggestions found
   * @throws IllegalStateException if spell checker not initialized
   * @throws IllegalArgumentException if queryString is null or blank
   */
  public String[] suggestSimilar(String queryString, int maxSuggestions) {
    validateInput(queryString);

    if (spellChecker == null) {
      throw new IllegalStateException("Spell checker not initialized");
    }

    // Accession-like queries: check accession field first
    if (looksLikeAccession(queryString)) {
      log.debug(
          "Query '{}' matches accession pattern, checking accession field first", queryString);

      String[] accessionSuggestions =
          suggestFromIndex(IndexName.SUBMISSION, FieldName.ACCESSION.getName(), queryString, maxSuggestions);

      if (accessionSuggestions.length > 0) {
        log.debug(
            "Found {} accession suggestions for '{}'", accessionSuggestions.length, queryString);
        return accessionSuggestions;
      }
    }

    // Standard cascade: EFO ontology → Submission content
    String[] efoSuggestions =
        suggestFromIndex(IndexName.EFO, EFOField.TERM.getFieldName(), queryString, maxSuggestions);

    if (efoSuggestions.length > 0) {
      log.debug("Found {} EFO term suggestions for '{}'", efoSuggestions.length, queryString);
      return efoSuggestions;
    }

    // Final fallback: general submission content
    log.debug("No EFO suggestions found, checking submission content for '{}'", queryString);
    String[] contentSuggestions =
        suggestFromIndex(IndexName.SUBMISSION, Constants.CONTENT, queryString, maxSuggestions);

    if (contentSuggestions.length == 0) {
      log.debug("No suggestions found in any index for '{}'", queryString);
    }

    return contentSuggestions;
  }

  /**
   * Suggests similar terms from a specific index and field.
   *
   * <p>This is a lower-level method used internally by the cascading suggestion logic. Uses {@link
   * DirectSpellChecker} with {@link SuggestMode#SUGGEST_WHEN_NOT_IN_INDEX} mode to only suggest
   * alternatives when the exact term is not found.
   *
   * @param indexName the index to search (e.g., EFO, SUBMISSION)
   * @param fieldName the field to check for similar terms (e.g., "term", "content", "accession")
   * @param queryString the query string to get suggestions for
   * @param maxSuggestions maximum number of suggestions to return
   * @return array of suggested spellings ordered by score, empty array if no suggestions found
   */
  private String[] suggestFromIndex(
      IndexName indexName, String fieldName, String queryString, int maxSuggestions) {

    IndexSearcher searcher = null;
    try {
      searcher = indexManager.acquireSearcher(indexName);
      IndexReader reader = searcher.getIndexReader();

      BytesRef queryBytes = new BytesRef(queryString);

      SuggestWord[] suggestions =
          spellChecker.suggestSimilar(
              new Term(fieldName, queryBytes),
              maxSuggestions,
              reader,
              SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);

      String[] results = new String[suggestions.length];
      for (int i = 0; i < suggestions.length; i++) {
        results[i] = suggestions[i].string;
        log.debug(
            "Suggestion from {} index, field '{}': '{}' (score: {}, freq: {})",
            indexName,
            fieldName,
            suggestions[i].string,
            suggestions[i].score,
            suggestions[i].freq);
      }

      return results;

    } catch (IOException e) {
      log.error(
          "Error getting spelling suggestions from {} index, field '{}' for query '{}'",
          indexName,
          fieldName,
          queryString,
          e);
      return new String[0]; // Return empty array on error
    } finally {
      if (searcher != null) {
        try {
          indexManager.releaseSearcher(indexName, searcher);
        } catch (IOException e) {
          log.error("Error releasing {} searcher after spell check", indexName, e);
        }
      }
    }
  }

  /**
   * Determines if a query string matches common accession number patterns.
   *
   * <p>Recognizes patterns like:
   *
   * <ul>
   *   <li>S-BSST1432, E-MTAB-123 (format: LETTER-LETTERS+DIGITS)
   *   <li>GSE12345, SRP123456 (format: 3+ LETTERS + DIGITS)
   * </ul>
   *
   * <p><strong>Note:</strong> Customize these patterns based on your specific accession formats.
   *
   * @param query the query string to check
   * @return true if the query matches accession patterns
   */
  private boolean looksLikeAccession(String query) {
    if (query == null || query.isBlank()) {
      return false;
    }
    // Pattern 1: S-BSST123, E-MTAB-123, E-MTAB-1234-5
    // Pattern 2: GSE12345, SRP123456, PRJ123
    return query.matches("^[A-Z]-[A-Z]+[-\\d].*") ||  // Handles dashes and digits after letters
        query.matches("^[A-Z]{3,}\\d+.*");          // 3+ caps followed by digits
  }


  /**
   * Validates input parameters for suggestion methods.
   *
   * @param queryString the query to validate
   * @throws IllegalArgumentException if query is null or blank
   */
  private void validateInput(String queryString) {
    if (queryString == null || queryString.isBlank()) {
      throw new IllegalArgumentException("Query string must not be null or blank");
    }
  }
}
