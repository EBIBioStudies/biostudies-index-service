package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearchHit;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearcher;
import uk.ac.ebi.biostudies.index_service.search.taxonomy.TaxonomyNode;

/**
 * Service providing autocomplete functionality by searching EFO (Experimental Factor Ontology)
 * terms and alternative terms, with optional filtering to ensure results exist in the submission
 * index.
 *
 * <p>This service supports two autocomplete modes:
 *
 * <ul>
 *   <li><b>Standard mode</b> - Searches EFO ontology structure without counts
 *   <li><b>Count mode</b> - Searches submission index with descendant-inclusive counts
 * </ul>
 */
@Slf4j
@Service
public class AutoCompleteService {
  private static final int MAX_LIMIT = 200;

  /**
   * Maximum number of child nodes to return when expanding an EFO tree node. Set higher than
   * autocomplete limit since ontology nodes can have many children.
   */
  private static final int MAX_TREE_LIMIT = 500;

  /**
   * Multiplier for initial fetch to account for filtering. Fetches extra results to ensure we have
   * enough after filtering by index presence.
   */
  private static final int FETCH_MULTIPLIER = 3;

  private final EFOSearcher efoSearcher;
  private final EFOHierarchyService efoHierarchyService;
  private final EFOTermCountService efoTermCountService;
  private final EFOTermMatcher efoTermMatcher;

  @Value("${autocomplete.filter-by-index:true}")
  private boolean filterByIndex;

  public AutoCompleteService(
      EFOSearcher efoSearcher,
      EFOHierarchyService efoHierarchyService,
      EFOTermCountService efoTermCountService,
      EFOTermMatcher efoTermMatcher) {
    this.efoSearcher = Objects.requireNonNull(efoSearcher, "efoSearcher must not be null");
    this.efoHierarchyService =
        Objects.requireNonNull(efoHierarchyService, "efoHierarchyService must not be null");
    this.efoTermCountService =
        Objects.requireNonNull(efoTermCountService, "efoTermCountService must not be null");
    this.efoTermMatcher = Objects.requireNonNull(efoTermMatcher, "efoTermMatcher must not be null");
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
   * @param query the search term to match against EFO terms (must not be null)
   * @param limit maximum number of results (capped at {@value MAX_LIMIT})
   * @return formatted autocomplete suggestions, one per line; empty string if query is null/empty
   *     or on error
   */
  public String getKeywords(String query, int limit) {
    if (query == null || query.trim().isEmpty()) {
      log.debug("Ignoring empty or null query");
      return "";
    }

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

      int fetchLimit = Math.min(limit * FETCH_MULTIPLIER, MAX_LIMIT);

      List<EFOSearchHit> efoSearchHits =
          efoSearcher.searchAll(
              luceneQuery,
              new Sort(new SortField(field, SortField.Type.STRING, false)),
              fetchLimit);

      log.debug("Fetched {} EFO term results before filtering", efoSearchHits.size());

      List<EFOSearchHit> filteredTerms;
      if (filterByIndex) {
        filteredTerms = filterByIndexPresence(efoSearchHits, limit, EFOField.TERM);
        log.debug("Filtered to {} term results present in submission index", filteredTerms.size());
      } else {
        filteredTerms = efoSearchHits.subList(0, Math.min(limit, efoSearchHits.size()));
      }

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

    try {
      List<EFOSearchHit> filteredHits = new ArrayList<>();
      int checkedCount = 0;
      int filteredCount = 0;

      for (EFOSearchHit hit : efoSearchHits) {
        checkedCount++;

        String termToCheck = getTermForFiltering(hit, sourceField);
        if (termToCheck == null || termToCheck.trim().isEmpty()) {
          log.trace("Skipping hit with null/empty term: {}", hit);
          filteredCount++;
          continue;
        }

        int docFreq = efoSearcher.getTermFrequency(termToCheck);
        if (docFreq > 0) {
          filteredHits.add(hit);
          log.trace("Term '{}' exists in {} documents", termToCheck, docFreq);

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
      return hit.term();
    } else if (sourceField == EFOField.ALTERNATIVE_TERMS) {
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

    for (EFOSearchHit hit : ontologyTerms) {
      resultStr.append(hit.term()).append("|o|");
      if (hit.child() != null) {
        resultStr.append(hit.id());
      }
      resultStr.append("\n");
    }

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
    if (query.contains("\"")
        || query.contains("AND")
        || query.contains("OR")
        || query.contains("*")) {
      return query;
    }

    return query + "*";
  }

  /**
   * Retrieves child nodes of a given EFO term for tree navigation.
   *
   * <p>Returns all direct children of the specified EFO node, sorted alphabetically, to support
   * hierarchical browsing of the ontology. Results are NOT filtered by submission index presence to
   * maintain the complete ontology structure - parent terms may not appear directly in submissions
   * even when their children do.
   *
   * @param efoId the EFO term identifier (URI) whose children to retrieve
   * @return pipe-delimited plain text with child terms in alphabetical order, format: {@code
   *     term|o|uri}; empty string if efoId is null/empty or on error
   */
  public String getEfoTree(String efoId) {
    if (efoId == null || efoId.trim().isEmpty()) {
      log.debug("Ignoring empty or null EFO ID");
      return "";
    }

    try {
      List<String> childTerms = efoHierarchyService.getChildrenByEfoId(efoId);
      if (childTerms.isEmpty()) {
        log.debug("No child nodes found for EFO term: {}", efoId);
        return "";
      }

      List<EFOSearchHit> children = new ArrayList<>(childTerms.size());
      String parentTerm = efoHierarchyService.getTerm(efoId);
      String parentLabel = parentTerm != null ? parentTerm : efoId;

      for (String childTerm : childTerms) {
        if (childTerm == null || childTerm.isBlank()) {
          continue;
        }

        String childId = efoTermMatcher.getEFOId(childTerm);
        children.add(
            new EFOSearchHit(null, childId, childTerm, parentLabel, null, List.of(), List.of()));
      }

      log.debug("Fetched {} child nodes for EFO term: {}", children.size(), efoId);
      return formatAutocompleteResponse(children, List.of());
    } catch (Exception e) {
      log.error("Error fetching EFO tree for EFO ID '{}': {}", efoId, e.getMessage(), e);
      return "";
    }
  }

  /**
   * Resolves an EFO ID for a search hit.
   *
   * @param hit the search hit
   * @return EFO ID if available, otherwise resolved from the term
   */
  private String resolveEfoId(EFOSearchHit hit) {
    if (hit == null) {
      return null;
    }
    if (hit.efoID() != null && !hit.efoID().isBlank()) {
      return hit.efoID();
    }
    if (hit.id() != null && !hit.id().isBlank()) {
      return hit.id();
    }
    return efoTermMatcher.getEFOId(hit.term());
  }

  /**
   * Searches for EFO terms with descendant-inclusive counts.
   *
   * <p>Autocomplete remains label-based, but counts now include the term and all descendants in the
   * ontology hierarchy.
   *
   * @param query the search term prefix
   * @param limit maximum number of results (capped at {@value MAX_LIMIT})
   * @return formatted autocomplete response with counts; empty string if query is null/empty or on
   *     error
   */
  public String getKeywordsWithCounts(String query, int limit) {
    if (query == null || query.trim().isEmpty()) {
      log.debug("Ignoring empty or null query");
      return "";
    }

    if (limit > MAX_LIMIT) {
      limit = MAX_LIMIT;
    }

    try {
      String modifiedQuery = modifyQuery(query);
      QueryParser parser = new QueryParser(EFOField.TERM.getFieldName(), new KeywordAnalyzer());
      Query luceneQuery = parser.parse(modifiedQuery);

      int fetchLimit = Math.min(limit * FETCH_MULTIPLIER, MAX_LIMIT);
      List<EFOSearchHit> searchHits =
          efoSearcher.searchAll(
              luceneQuery,
              new Sort(new SortField(EFOField.TERM.getFieldName(), SortField.Type.STRING, false)),
              fetchLimit);

      if (searchHits.isEmpty()) {
        return "";
      }

      List<TaxonomyNode> nodes = new ArrayList<>();
      for (EFOSearchHit hit : searchHits) {
        if (hit == null || hit.term() == null || hit.term().isBlank()) {
          continue;
        }

        String efoId = resolveEfoId(hit);
        if (efoId == null || efoId.isBlank()) {
          continue;
        }

        long count = efoTermCountService.countIncludingDescendantsByEfoId(efoId);
        if (count > 0) {
          boolean hasChildren = hasChildrenWithMatches(efoId);

          nodes.add(new TaxonomyNode(hit.term(), Math.toIntExact(count), efoId, hasChildren));
        }

        if (nodes.size() >= limit) {
          break;
        }
      }

      log.debug("EFO search completed: {} results, {} total hits", nodes.size(), nodes.size());
      return formatAutocompleteNodes(nodes);

    } catch (ParseException e) {
      log.error("Failed to parse query '{}': {}", query, e.getMessage(), e);
    } catch (Exception e) {
      log.error("Error executing taxonomy search for query '{}': {}", query, e.getMessage(), e);
    }

    return "";
  }

  private boolean hasChildrenWithMatches(String efoId) {
    try {
      List<String> childTerms = efoHierarchyService.getChildrenByEfoId(efoId);
      if (childTerms == null || childTerms.isEmpty()) {
        return false;
      }

      for (String childTerm : childTerms) {
        if (childTerm == null || childTerm.isBlank()) {
          continue;
        }
        String childId = efoTermMatcher.getEFOId(childTerm);
        if (childId == null || childId.isBlank()) {
          continue;
        }

        long childCount = efoTermCountService.countIncludingDescendantsByEfoId(childId);
        if (childCount > 0) {
          return true; // at least one child with matches, so show expand
        }
      }
    } catch (Exception e) {
      log.warn("Error checking children with matches for EFO ID '{}'", efoId, e);
    }
    return false;
  }


  /**
   * Retrieves children of a given EFO term with descendant-inclusive counts.
   *
   * <p>Returned nodes are direct children, but each count includes that child term and all of its
   * descendants.
   *
   * @param efoId the parent EFO term URI
   * @param limit maximum number of children (defaults to {@value MAX_TREE_LIMIT} if &lt;= 0)
   * @return formatted response with child terms and counts; empty string if efoId is null/empty or
   *     on error
   */
  public String getEfoTreeWithCounts(String efoId, int limit) {
    if (efoId == null || efoId.trim().isEmpty()) {
      log.debug("Ignoring empty or null EFO ID");
      return "";
    }

    if (limit <= 0) {
      limit = MAX_TREE_LIMIT;
    }
    boolean atLeastOneChildWithMatches = false;

    try {
      List<String> childTerms = efoHierarchyService.getChildrenByEfoId(efoId);
      if (childTerms.isEmpty()) {
        return "";
      }

      List<TaxonomyNode> children = new ArrayList<>();
      for (String childTerm : childTerms) {
        if (childTerm == null || childTerm.isBlank()) {
          continue;
        }

        String childId = efoTermMatcher.getEFOId(childTerm);
        if (childId == null || childId.isBlank()) {
          continue;
        }

        long count = efoTermCountService.countIncludingDescendantsByEfoId(childId);
        if (count > 0) {
          boolean hasChildren = efoHierarchyService.hasChildrenByEfoId(childId);
          atLeastOneChildWithMatches = true;
          children.add(new TaxonomyNode(childTerm, Math.toIntExact(count), childId, hasChildren));
        }

        if (children.size() >= limit) {
          break;
        }
      }

      log.debug("Fetched {} child nodes for EFO term: {}", children.size(), efoId);
      return formatAutocompleteNodes(children);

    } catch (Exception e) {
      log.error("Error fetching EFO tree children for EFO ID '{}': {}", efoId, e.getMessage(), e);
      return "";
    }
  }

  private String formatAutocompleteNodes(List<TaxonomyNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return "";
    }

    StringBuilder result = new StringBuilder();
    for (TaxonomyNode node : nodes) {
      String line =
          node.term()
              + "|o|"
              + (node.hasChildren() && node.efoId() != null ? node.efoId() : "")
              + "|"
              + node.count();
      result.append(line).append("\n");
    }
    return result.toString();
  }
}
