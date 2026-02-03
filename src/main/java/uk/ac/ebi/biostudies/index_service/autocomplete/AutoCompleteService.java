package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearchHit;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearcher;

/**
 * Service providing autocomplete functionality by searching EFO (Experimental Factor Ontology)
 * terms and alternative terms, with optional filtering to ensure results exist in the submission
 * index.
 */
@Slf4j
@Service
public class AutoCompleteService {
  private static final int MAX_LIMIT = 200;

  /**
   * Multiplier for initial fetch to account for filtering. Fetches extra results to ensure we have
   * enough after filtering by index presence.
   */
  private static final int FETCH_MULTIPLIER = 3;

  private final EFOSearcher efoSearcher;
  private final IndexManager indexManager;

  @Value("${autocomplete.filter-by-index:true}")
  private boolean filterByIndex;

  public AutoCompleteService(EFOSearcher efoSearcher, IndexManager indexManager) {
    this.efoSearcher = efoSearcher;
    this.indexManager = indexManager;
  }

  /**
   * Searches for EFO terms matching the query and formats them for autocomplete.
   *
   * <p>First searches primary EFO terms. If fewer results than the limit are found, also searches
   * alternative terms to fill the remaining slots. Applies wildcard suffix to simple queries for
   * prefix matching.
   *
   * <p>When filtering is enabled, results are filtered to only include terms that exist in the
   * submission index, ensuring users only see suggestions that will return actual search results.
   *
   * <p>Results are formatted as pipe-delimited lines in two formats:
   *
   * <ul>
   *   <li>{@code term|o|uri} - Ontology term, URI included only if term has children (is
   *       expandable)
   *   <li>{@code term|t|content} - Alternative term from content
   * </ul>
   *
   * @param query the search term to match against EFO terms
   * @param limit maximum number of results (capped at {@value MAX_LIMIT})
   * @return formatted autocomplete suggestions, one per line
   */
  public String getKeywords(String query, int limit) {
    if (limit > MAX_LIMIT) {
      limit = MAX_LIMIT;
    }

    String result = "";
    String field = EFOField.TERM.getFieldName();

    try {
      QueryParser parser = new QueryParser(field, new KeywordAnalyzer());
      String modifiedQuery = modifyQuery(query);
      Query luceneQuery = parser.parse(modifiedQuery);

      log.debug("Executing autocomplete query: {}", luceneQuery);

      // Fetch extra results to account for filtering
      int fetchLimit = Math.min(limit * FETCH_MULTIPLIER, MAX_LIMIT);

      List<EFOSearchHit> efoSearchHits =
          efoSearcher.searchAll(
              luceneQuery,
              new Sort(new SortField(field, SortField.Type.STRING, false)),
              fetchLimit);

      log.debug("Fetched {} EFO term results before filtering", efoSearchHits.size());

      // Filter primary results by index presence
      List<EFOSearchHit> filteredTerms;
      if (filterByIndex) {
        filteredTerms = filterByIndexPresence(efoSearchHits, limit, EFOField.TERM);
        log.debug("Filtered to {} term results present in submission index", filteredTerms.size());
      } else {
        filteredTerms = efoSearchHits.subList(0, Math.min(limit, efoSearchHits.size()));
      }

      // If we don't have enough results, fetch alternative terms to fill the gap
      List<EFOSearchHit> filteredAlternatives = List.of();
      if (filteredTerms.size() < limit) {
        int remainingLimit = limit - filteredTerms.size();
        int altFetchLimit = Math.min(remainingLimit * FETCH_MULTIPLIER, MAX_LIMIT);

        List<EFOSearchHit> alternativeTerms = searchAlternativeTerms(modifiedQuery, altFetchLimit);
        log.debug("Fetched {} alternative term results before filtering", alternativeTerms.size());

        if (filterByIndex) {
          filteredAlternatives =
              filterByIndexPresence(alternativeTerms, remainingLimit, EFOField.ALTERNATIVE_TERMS);
          log.debug(
              "Filtered to {} alternative term results present in submission index",
              filteredAlternatives.size());
        } else {
          filteredAlternatives =
              alternativeTerms.subList(0, Math.min(remainingLimit, alternativeTerms.size()));
        }
      }

      result = formatAutocompleteResponse(filteredTerms, filteredAlternatives);

    } catch (ParseException e) {
      log.error("Failed to parse query '{}': {}", query, e.getMessage(), e);
    } catch (Exception e) {
      log.error("Error executing autocomplete search for query '{}': {}", query, e.getMessage(), e);
    }

    return result;
  }

  /**
   * Searches alternative EFO terms that match the query.
   *
   * <p>Alternative terms are synonyms and related terms from the EFO ontology that may not be the
   * primary term name.
   *
   * @param queryString the query string (already modified with wildcards if applicable)
   * @param limit maximum number of alternative terms to fetch
   * @return list of search hits from alternative terms field
   * @throws Exception if query parsing or search fails
   */
  private List<EFOSearchHit> searchAlternativeTerms(String queryString, int limit)
      throws Exception {
    String altField = EFOField.ALTERNATIVE_TERMS.getFieldName();
    QueryParser parser = new QueryParser(altField, new KeywordAnalyzer());
    Query query = parser.parse(queryString);
    return efoSearcher.searchAll(
        query, new Sort(new SortField(altField, SortField.Type.STRING, false)), limit);
  }

  /**
   * Filters EFO search hits to only include terms that exist in the submission index.
   *
   * <p>For primary terms, checks if the term exists in the content field. For alternative terms,
   * checks the same field since alternative terms should also map to searchable content.
   *
   * @param efoSearchHits all hits from EFO search
   * @param limit maximum number of filtered results to return
   * @param sourceField the EFO field these hits came from (TERM or ALTERNATIVE_TERMS)
   * @return filtered list containing only terms that exist in at least one document
   */
  private List<EFOSearchHit> filterByIndexPresence(
      List<EFOSearchHit> efoSearchHits, int limit, EFOField sourceField) {
    if (efoSearchHits == null || efoSearchHits.isEmpty()) {
      return List.of();
    }

    IndexSearcher searcher = null;
    try {
      searcher = indexManager.acquireSearcher(IndexName.SUBMISSION);
      IndexReader reader = searcher.getIndexReader();

      List<EFOSearchHit> filteredHits = new ArrayList<>();
      int checkedCount = 0;
      int filteredCount = 0;

      for (EFOSearchHit hit : efoSearchHits) {
        checkedCount++;

        // Get the actual term value to check
        String termToCheck = getTermForFiltering(hit, sourceField);
        if (termToCheck == null || termToCheck.isEmpty()) {
          log.trace("Skipping hit with null/empty term: {}", hit);
          filteredCount++;
          continue;
        }

        // Check if term exists in the index with at least 1 document
        Term luceneTerm = new Term(Constants.CONTENT, termToCheck.toLowerCase());
        int docFreq = reader.docFreq(luceneTerm);

        if (docFreq > 0) {
          filteredHits.add(hit);
          log.trace("Term '{}' exists in {} documents", termToCheck, docFreq);

          // Stop once we have enough results
          if (filteredHits.size() >= limit) {
            break;
          }
        } else {
          filteredCount++;
          log.trace("Term '{}' not found in index, filtering out", termToCheck);
        }
      }

      if (filteredCount > 0) {
        log.debug(
            "Filtered {} of {} checked {} terms (not present in index)",
            filteredCount,
            checkedCount,
            sourceField.getFieldName());
      }

      return filteredHits;

    } catch (IOException e) {
      log.warn(
          "Error filtering autocomplete terms by index presence, returning unfiltered results", e);
      return efoSearchHits.subList(0, Math.min(limit, efoSearchHits.size()));
    } finally {
      if (searcher != null) {
        try {
          indexManager.releaseSearcher(IndexName.SUBMISSION, searcher);
        } catch (IOException e) {
          log.error("Error releasing searcher after autocomplete filtering", e);
        }
      }
    }
  }

  /**
   * Extracts the appropriate term value from a search hit based on the source field.
   *
   * <p>For primary EFO terms, uses the term field. For alternative terms, uses the altTerm field.
   *
   * @param hit the EFO search hit
   * @param sourceField the field the hit came from
   * @return the term value to use for index presence checking, or null if not available
   */
  private String getTermForFiltering(EFOSearchHit hit, EFOField sourceField) {
    if (sourceField == EFOField.TERM) {
      // Primary terms: use the term field
      return hit.term();
    } else if (sourceField == EFOField.ALTERNATIVE_TERMS) {
      // Alternative terms: use the altTerm field
      return hit.altTerm();
    }
    return null;
  }

  /**
   * Formats search hits into pipe-delimited autocomplete response.
   *
   * <p>Combines primary ontology terms and alternative terms into a single response. Each line
   * follows one of two formats:
   *
   * <ul>
   *   <li>{@code term|o|uri} - Ontology term, where 'o' indicates ontology and URI is included only
   *       if term has children (is expandable)
   *   <li>{@code term|t|content} - Alternative term, where 't' indicates text/content term
   * </ul>
   *
   * @param ontologyTerms the primary EFO ontology term results
   * @param alternativeTerms the alternative/synonym term results
   * @return formatted autocomplete suggestions, one per line
   */
  private String formatAutocompleteResponse(
      List<EFOSearchHit> ontologyTerms, List<EFOSearchHit> alternativeTerms) {
    StringBuilder resultStr = new StringBuilder();

    // Format primary ontology terms
    for (EFOSearchHit hit : ontologyTerms) {
      resultStr.append(hit.term()).append("|o|");

      // Include URI only if term has children (is expandable)
      if (hit.child() != null) {
        resultStr.append(hit.id());
      }

      resultStr.append("\n");
    }

    // Format alternative terms
    for (EFOSearchHit hit : alternativeTerms) {
      resultStr.append(hit.term()).append("|t|content\n");
    }

    return resultStr.toString();
  }

  /**
   * Modifies the query to support prefix matching by appending wildcard.
   *
   * <p>Leaves queries unchanged if they already contain:
   *
   * <ul>
   *   <li>Quotes (phrase searches)
   *   <li>Boolean operators (AND, OR) - must be uppercase per Lucene syntax
   *   <li>Wildcards (*)
   * </ul>
   *
   * @param query the original query string
   * @return the modified query with wildcard suffix if applicable
   */
  private String modifyQuery(String query) {
    // Don't modify if query already contains special operators
    // Note: Boolean operators are case-sensitive (must be uppercase per Lucene syntax)
    if (query.contains("\"")
        || query.contains("AND")
        || query.contains("OR")
        || query.contains("*")) {
      return query;
    }

    // Add wildcard for prefix matching
    return query + "*";
  }
}
