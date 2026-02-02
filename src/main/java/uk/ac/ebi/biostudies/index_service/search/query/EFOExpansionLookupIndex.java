package uk.ac.ebi.biostudies.index_service.search.query;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.exceptions.SearchException;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearchHit;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearcher;

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
 *
 * <p>This service is designed to be fault-tolerant - all exceptions are caught and logged,
 * returning empty expansion results to allow searches to continue with the original query.
 */
@Slf4j
@Service
public class EFOExpansionLookupIndex {

  /** Maximum number of EFO index hits to retrieve for expansion. */
  private static final int MAX_INDEX_HITS = 16;

  private final EFOSearcher efoSearcher;

  /**
   * Constructs an EFO expansion lookup service.
   *
   * @param efoSearcher the searcher for querying the EFO index
   * @throws NullPointerException if efoSearcher is null
   */
  public EFOExpansionLookupIndex(EFOSearcher efoSearcher) {
    this.efoSearcher = java.util.Objects.requireNonNull(efoSearcher, "efoSearcher must not be null");
  }

  /**
   * Retrieves EFO expansion terms (synonyms and ontology terms) for a given query.
   *
   * <p>The method searches the EFO index for matching terms and returns any found synonyms and EFO
   * identifiers. The original query is converted to search the EFO index's "term" field.
   *
   * <p>This method never throws exceptions - all errors are caught and logged, returning an empty
   * expansion result to allow the search to continue with the original query.
   *
   * @param originalQuery the original query from the user
   * @return expansion terms containing synonyms and EFO terms (never null, may be empty)
   */
  public EFOExpansionTerms getExpansionTerms(Query originalQuery) {
    EFOExpansionTerms expansion = new EFOExpansionTerms();

    try {
      // Convert query to search EFO index (changes field to EFO_TERM)
      Query efoQuery = convertQueryForEfoIndex(originalQuery);

      if (efoQuery == null) {
        log.debug("Could not convert query to EFO format: {}", originalQuery);
        return expansion;
      }

      // Search EFO index using the facade
      List<EFOSearchHit> hits = efoSearcher.searchAll(efoQuery, MAX_INDEX_HITS);

      if (hits.isEmpty()) {
        log.debug("No EFO expansion hits found for query: {}", originalQuery);
        return expansion;
      }

      log.debug("Found {} EFO expansion hits for query: {}", hits.size(), originalQuery);

      // Extract expansion terms from hits
      for (EFOSearchHit hit : hits) {
        extractExpansionTerms(hit, expansion);
      }

      log.debug(
          "Expansion result for '{}': {} synonyms, {} EFO terms",
          originalQuery,
          expansion.synonyms.size(),
          expansion.efo.size());

    } catch (SearchException ex) {
      log.error("Search error while expanding terms for query: {}", originalQuery, ex);
      // Return empty expansion - allows search to continue with original query
    } catch (Exception ex) {
      log.error("Unexpected error while expanding terms for query: {}", originalQuery, ex);
      // Return empty expansion - allows search to continue with original query
    }

    return expansion;
  }

  /**
   * Extracts synonym and EFO terms from an EFO search hit and adds them to the expansion.
   *
   * <p>Filters out null, empty, or whitespace-only terms.
   *
   * @param efoSearchHit the EFO search hit containing synonyms and EFO terms
   * @param expansion the expansion object to populate
   */
  private void extractExpansionTerms(EFOSearchHit efoSearchHit, EFOExpansionTerms expansion) {
    // Extract synonym terms
    if (efoSearchHit.synonyms() != null && !efoSearchHit.synonyms().isEmpty()) {
      efoSearchHit.synonyms().stream()
          .filter(s -> s != null && !s.trim().isEmpty())
          .forEach(expansion.synonyms::add);
    }

    // Extract EFO ontology terms
    if (efoSearchHit.efoTerms() != null && !efoSearchHit.efoTerms().isEmpty()) {
      efoSearchHit.efoTerms().stream()
          .filter(s -> s != null && !s.trim().isEmpty())
          .forEach(expansion.efo::add);
    }
  }

  /**
   * Converts a query to search the EFO index by changing its field to the EFO term field.
   *
   * <p>The EFO index uses a standard field name ({@link EFOField#QE_TERM}) for all lookups, so we
   * rewrite the query to target this field regardless of the original query's target field.
   *
   * <p>Supported query types:
   * <ul>
   *   <li>{@link TermQuery}
   *   <li>{@link PhraseQuery}
   *   <li>{@link PrefixQuery}
   *   <li>{@link WildcardQuery}
   *   <li>{@link FuzzyQuery}
   *   <li>{@link TermRangeQuery}
   * </ul>
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

  /**
   * Converts a term query to search the EFO term field.
   */
  private Query convertTermQuery(TermQuery query) {
    Term originalTerm = query.getTerm();
    return new TermQuery(new Term(EFOField.QE_TERM.getFieldName(), originalTerm.text()));
  }

  /**
   * Converts a phrase query to search the EFO term field.
   *
   * <p>Multi-word phrases are combined into a single term for EFO lookup.
   */
  private Query convertPhraseQuery(PhraseQuery query) {
    Term[] terms = query.getTerms();
    if (terms.length == 0) {
      log.warn("Empty phrase query cannot be converted: {}", query);
      return null;
    }

    // Combine multi-word phrase into single term for EFO lookup
    String combinedText =
        Arrays.stream(terms).map(Term::text).reduce((a, b) -> a + " " + b).orElse("");

    return new TermQuery(new Term(EFOField.QE_TERM.getFieldName(), combinedText));
  }

  /**
   * Converts a prefix query to search the EFO term field.
   */
  private Query convertPrefixQuery(PrefixQuery query) {
    Term originalTerm = query.getPrefix();
    return new PrefixQuery(new Term(EFOField.QE_TERM.getFieldName(), originalTerm.text()));
  }

  /**
   * Converts a wildcard query to search the EFO term field.
   */
  private Query convertWildcardQuery(WildcardQuery query) {
    Term originalTerm = query.getTerm();
    return new WildcardQuery(new Term(EFOField.QE_TERM.getFieldName(), originalTerm.text()));
  }

  /**
   * Converts a fuzzy query to search the EFO term field, preserving fuzziness parameters.
   */
  private Query convertFuzzyQuery(FuzzyQuery query) {
    Term originalTerm = query.getTerm();
    int maxEdits = query.getMaxEdits();
    int prefixLength = query.getPrefixLength();
    return new FuzzyQuery(
        new Term(EFOField.QE_TERM.getFieldName(), originalTerm.text()), maxEdits, prefixLength);
  }

  /**
   * Converts a term range query to search the EFO term field, preserving range bounds.
   */
  private Query convertTermRangeQuery(TermRangeQuery query) {
    return new TermRangeQuery(
        EFOField.QE_TERM.getFieldName(),
        query.getLowerTerm(),
        query.getUpperTerm(),
        query.includesLower(),
        query.includesUpper());
  }
}
