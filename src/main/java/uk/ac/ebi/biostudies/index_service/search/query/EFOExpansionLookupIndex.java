package uk.ac.ebi.biostudies.index_service.search.query;

import java.io.IOException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOIndexFields;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Looks up EFO (Experimental Factor Ontology) expansion terms for query terms.
 *
 * <p>This service searches a separate EFO Lucene index to find synonyms and related ontology terms
 * that can be added to the original query for better recall. The EFO index is maintained separately
 * and contains mappings between scientific terms and their ontological relationships.
 *
 * <p>Example: Searching for "leukemia" might return:
 *
 * <ul>
 *   <li>Synonyms: "leukaemia" (British spelling), "blood cancer"
 *   <li>EFO terms: "acute myeloid leukemia", "chronic lymphocytic leukemia"
 * </ul>
 */
@Slf4j
@Service
public class EFOExpansionLookupIndex {

  /** Maximum number of EFO index hits to retrieve */
  private static final int MAX_INDEX_HITS = 16;

  private final IndexManager indexManager;

  public EFOExpansionLookupIndex(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  /**
   * Retrieves EFO expansion terms (synonyms and ontology terms) for a given query.
   *
   * <p>The method searches the EFO index for matching terms and returns any found synonyms and EFO
   * identifiers. Properly manages index searcher lifecycle using acquire/release pattern.
   *
   * <p>This method never throws exceptions - all errors are caught and logged, returning an empty
   * expansion result to allow the search to continue with the original query.
   *
   * @param originalQuery the original query from the user
   * @return expansion terms containing synonyms and EFO terms (never null, may be empty)
   */
  public EFOExpansionTerms getExpansionTerms(Query originalQuery) {
    EFOExpansionTerms expansion = new EFOExpansionTerms();
    IndexSearcher searcher = null;

    try {
      // Acquire searcher from pool
      searcher = indexManager.acquireSearcher(IndexName.EFO);

      // Convert query to search EFO index (changes field to "term")
      Query efoQuery = convertQueryForEfoIndex(originalQuery);

      if (efoQuery == null) {
        log.debug("Could not convert query to EFO format: {}", originalQuery);
        return expansion;
      }

      // Search EFO index
      TopDocs hits = searcher.search(efoQuery, MAX_INDEX_HITS);

      if (hits.scoreDocs.length == 0) {
        log.debug("No EFO expansion hits found for query: {}", originalQuery);
        return expansion;
      }

      log.debug("Found {} EFO expansion hits for query: {}", hits.scoreDocs.length, originalQuery);

      // Extract expansion terms from hits
      StoredFields storedFields = searcher.storedFields();
      for (ScoreDoc scoreDoc : hits.scoreDocs) {
        Document doc = storedFields.document(scoreDoc.doc);
        extractExpansionTerms(doc, expansion);
      }

      log.debug(
          "Expansion result for '{}': {} synonyms, {} EFO terms",
          originalQuery,
          expansion.synonyms.size(),
          expansion.efo.size());

    } catch (IOException ex) {
      log.error("IO error while expanding terms for query: {}", originalQuery, ex);
      // Return empty expansion - allows search to continue with original query
    } catch (Exception ex) {
      log.error("Unexpected error while expanding terms for query: {}", originalQuery, ex);
      // Return empty expansion - allows search to continue with original query
    } finally {
      // Always release searcher back to pool
      if (searcher != null) {
        releaseSearcherSafely(searcher);
      }
    }

    return expansion;
  }

  /**
   * Extracts synonym and EFO terms from a document and adds them to the expansion.
   *
   * @param doc the Lucene document from the EFO index
   * @param expansion the expansion object to populate
   */
  private void extractExpansionTerms(Document doc, EFOExpansionTerms expansion) {
    // Get synonym terms
    String[] synonyms = doc.getValues(EFOIndexFields.TERM);
    if (synonyms != null && synonyms.length > 0) {
      // Filter out empty/whitespace-only terms
      Arrays.stream(synonyms)
          .filter(s -> s != null && !s.trim().isEmpty())
          .forEach(expansion.synonyms::add);
    }

    // Get EFO ontology terms
    String[] efoTerms = doc.getValues(EFOIndexFields.EFO);
    if (efoTerms != null && efoTerms.length > 0) {
      // Filter out empty/whitespace-only terms
      Arrays.stream(efoTerms)
          .filter(s -> s != null && !s.trim().isEmpty())
          .forEach(expansion.efo::add);
    }
  }

  /**
   * Safely releases the searcher back to the pool, logging any errors.
   *
   * @param searcher the searcher to release
   */
  private void releaseSearcherSafely(IndexSearcher searcher) {
    try {
      indexManager.releaseSearcher(IndexName.EFO, searcher);
    } catch (IOException ex) {
      log.error("Error releasing EFO searcher", ex);
      // Don't rethrow - we're in cleanup code
    } catch (Exception ex) {
      log.error("Unexpected error releasing EFO searcher", ex);
      // Don't rethrow - we're in cleanup code
    }
  }

  /**
   * Converts a query to search the EFO index by changing its field to "term".
   *
   * <p>The EFO index uses a standard field name "term" for all lookups, so we need to rewrite the
   * query regardless of what field it originally targeted.
   *
   * @param originalQuery the original query
   * @return a query suitable for searching the EFO index, or null if conversion fails or query type
   *     is unsupported
   */
  private Query convertQueryForEfoIndex(Query originalQuery) {
    if (originalQuery == null) {
      log.warn("Cannot convert null query to EFO format");
      return null;
    }

    try {
      if (originalQuery instanceof TermQuery) {
        return convertTermQuery((TermQuery) originalQuery);

      } else if (originalQuery instanceof PhraseQuery) {
        return convertPhraseQuery((PhraseQuery) originalQuery);

      } else if (originalQuery instanceof PrefixQuery) {
        return convertPrefixQuery((PrefixQuery) originalQuery);

      } else if (originalQuery instanceof WildcardQuery) {
        return convertWildcardQuery((WildcardQuery) originalQuery);

      } else if (originalQuery instanceof FuzzyQuery) {
        return convertFuzzyQuery((FuzzyQuery) originalQuery);

      } else if (originalQuery instanceof TermRangeQuery) {
        return convertTermRangeQuery((TermRangeQuery) originalQuery);

      } else {
        log.debug(
            "Unsupported query type for EFO expansion: {} (query: {})",
            originalQuery.getClass().getSimpleName(),
            originalQuery);
        return null;
      }

    } catch (Exception ex) {
      log.error("Error converting query to EFO format: {}", originalQuery, ex);
      return null;
    }
  }

  private Query convertTermQuery(TermQuery query) {
    Term originalTerm = query.getTerm();
    return new TermQuery(new Term(EFOIndexFields.TERM, originalTerm.text()));
  }

  private Query convertPhraseQuery(PhraseQuery query) {
    Term[] terms = query.getTerms();
    if (terms.length == 0) {
      log.warn("Empty phrase query cannot be converted: {}", query);
      return null;
    }

    // Convert multi-word phrase to single term for EFO lookup
    String combinedText =
        Arrays.stream(terms).map(Term::text).reduce((a, b) -> a + " " + b).orElse("");

    return new TermQuery(new Term(EFOIndexFields.TERM, combinedText));
  }

  private Query convertPrefixQuery(PrefixQuery query) {
    Term originalTerm = query.getPrefix();
    return new PrefixQuery(new Term(EFOIndexFields.TERM, originalTerm.text()));
  }

  private Query convertWildcardQuery(WildcardQuery query) {
    Term originalTerm = query.getTerm();
    return new WildcardQuery(new Term(EFOIndexFields.TERM, originalTerm.text()));
  }

  private Query convertFuzzyQuery(FuzzyQuery query) {
    Term originalTerm = query.getTerm();
    int maxEdits = query.getMaxEdits();
    int prefixLength = query.getPrefixLength();
    return new FuzzyQuery(
        new Term(EFOIndexFields.TERM, originalTerm.text()), maxEdits, prefixLength);
  }

  private Query convertTermRangeQuery(TermRangeQuery query) {
    return new TermRangeQuery(
        EFOIndexFields.TERM,
        query.getLowerTerm(),
        query.getUpperTerm(),
        query.includesLower(),
        query.includesUpper());
  }
}
