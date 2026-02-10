package uk.ac.ebi.biostudies.index_service.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.search.engine.PaginatedResult;
import uk.ac.ebi.biostudies.index_service.search.query.QueryResult;
import uk.ac.ebi.biostudies.index_service.search.searchers.SubmissionSearchHit;
import uk.ac.ebi.biostudies.index_service.search.suggestion.SpellCheckSuggestionService;

/**
 * Processes and enhances search results before building the final response.
 *
 * <p>This component applies the following enhancements to raw search results:
 *
 * <ul>
 *   <li><strong>Snippet extraction</strong> - Replaces full content with highlighted snippets
 *   <li><strong>Spell suggestions</strong> - Generates alternative spellings for poor results
 *   <li><strong>Term filtering</strong> - Filters EFO/synonym expansions to only existing terms
 *   <li><strong>Facet formatting</strong> - Formats facets for UI display
 * </ul>
 *
 * <p>Use {@link #buildEnrichedResponse} to apply all enhancements and build the response DTO.
 */
@Slf4j
@Component
public class SearchResponseProcessor {

  private static final int MAX_SUGGESTIONS = 5;

  private static final int DEFAULT_FACET_LIMIT = 20;

  private static final String RELEVANCE = "relevance";

  private final IndexManager indexManager;
  private final SearchSnippetExtractor snippetExtractor;
  private final FacetFormatter facetFormatter;
  private final SpellCheckSuggestionService suggestionService;

  public SearchResponseProcessor(
      IndexManager indexManager,
      SearchSnippetExtractor snippetExtractor,
      FacetFormatter facetFormatter, SpellCheckSuggestionService suggestionService) {
    this.indexManager = indexManager;
    this.snippetExtractor = snippetExtractor;
    this.facetFormatter = facetFormatter;
    this.suggestionService = suggestionService;
  }

  /**
   * Builds an enriched search response by applying multiple enhancements to raw results.
   *
   * <p><strong>Applied enhancements:</strong>
   *
   * <ol>
   *   <li>Content snippet extraction with query highlighting
   *   <li>Spelling suggestion generation (if results are poor)
   *   <li>EFO/synonym term filtering (show only terms present in index)
   *   <li>Facet formatting for UI display
   * </ol>
   *
   * <p>This is a convenience method that orchestrates all result processing steps. If you need
   * fine-grained control, consider calling individual enhancement methods directly.
   *
   * @param request the original search request with user parameters
   * @param result the paginated search results from Lucene
   * @param originalQueryString the original query string before preprocessing
   * @param queryResult the query result containing the Lucene query and expansion terms
   * @param drillDownQuery the drill-down query with applied facet filters
   * @return complete, enriched search response DTO ready for serialization
   * @see #extractSnippets(List, Query)
   * @see #getSpellingSuggestions(String, PaginatedResult)
   * @see #filterTermsByIndexPresence(Set, String)
   */
  public SearchResponseDTO buildEnrichedResponse(
      SearchRequest request,
      PaginatedResult<SubmissionSearchHit> result,
      String originalQueryString,
      QueryResult queryResult,
      DrillDownQuery drillDownQuery) {

    // 1. Get hits from the paginated result
    List<SubmissionSearchHit> hits = result.results();
    // 2. Apply snippet extraction to the content field
    List<SubmissionSearchHit> hitsWithSnippets = extractSnippets(hits, queryResult.getQuery());
    log.debug("Extracted snippets for {} hits", hitsWithSnippets.size());

    // 3. Get spelling suggestions if needed
    List<String> suggestions = getSpellingSuggestions(originalQueryString, result);

    int facetLimit =
        request.getFacetLimit() != null ? request.getFacetLimit() : DEFAULT_FACET_LIMIT;
    List<FacetDimensionDTO> facets =
        facetFormatter.getFacetsForUI(
            request.getCollection(), drillDownQuery, facetLimit, request.getFacets());

    // 4. Build response
    return buildSearchResponse(request, queryResult, result, facets, hitsWithSnippets, suggestions);
  }

  /**
   * Filters expanded terms to only include those that actually exist in the submission index.
   *
   * <p>This ensures that EFO expanded terms and synonyms shown to users are grounded in actual
   * searchable content, matching the behavior of spell suggestions. Terms are filtered by checking
   * their document frequency in the specified field.
   *
   * <p><strong>Why filter?</strong> EFO ontology may contain thousands of child terms, but only a
   * subset exist in your actual indexed documents. Showing non-existent terms confuses users and
   * creates false expectations.
   *
   * <p><strong>Performance:</strong> Uses acquireSearcher/releaseSearcher for thread-safe access.
   * Term existence checks are fast (O(1) hash table lookups in Lucene's term dictionary).
   *
   * @param expandedTerms all terms from EFO expansion or synonym expansion
   * @param fieldName the field to check for term presence (typically "content")
   * @return filtered set containing only terms that exist in at least one document
   */
  private Set<String> filterTermsByIndexPresence(Set<String> expandedTerms, String fieldName) {
    if (expandedTerms == null || expandedTerms.isEmpty()) {
      return Set.of();
    }

    IndexSearcher searcher = null;
    try {
      searcher = indexManager.acquireSearcher(IndexName.SUBMISSION);
      IndexReader reader = searcher.getIndexReader();

      Set<String> existingTerms = new HashSet<>();
      int filteredCount = 0;

      for (String term : expandedTerms) {
        // Check if term exists in the index with at least 1 document
        Term luceneTerm = new Term(fieldName, term.toLowerCase());
        int docFreq = reader.docFreq(luceneTerm);

        if (docFreq > 0) {
          existingTerms.add(term);
          log.debug("Term '{}' exists in {} documents", term, docFreq);
        } else {
          filteredCount++;
          log.debug("Term '{}' not found in index, filtering out", term);
        }
      }

      if (filteredCount > 0) {
        log.info(
            "Filtered {} of {} expanded terms (not present in index)",
            filteredCount,
            expandedTerms.size());
      }

      return existingTerms;

    } catch (IOException e) {
      log.warn("Error filtering terms by index presence, returning all terms", e);
      return expandedTerms; // Fallback: return all if check fails
    } finally {
      if (searcher != null) {
        try {
          indexManager.releaseSearcher(IndexName.SUBMISSION, searcher);
        } catch (IOException e) {
          log.error("Error releasing searcher after term filtering", e);
        }
      }
    }
  }

  /**
   * Generates spelling suggestions for the query if search results are poor or empty.
   *
   * <p>Suggestions are generated using {@link org.apache.lucene.search.spell.DirectSpellChecker}
   * which analyzes the submission index's content field for similar terms based on edit distance.
   *
   * <p><strong>Suggestion criteria:</strong>
   *
   * <ul>
   *   <li>Query string must be non-empty
   *   <li>Spell checker must be initialized
   *   <li>Search results must be poor (5 or fewer hits)
   * </ul>
   *
   * <p><strong>Why only suggest on poor results?</strong> If the search returned many results, the
   * query was likely correct. Suggestions are only helpful when the user might have made a typo.
   *
   * @param queryString the original query string from the user
   * @param paginatedResult the search results to evaluate quality
   * @return list of up to 5 alternative spellings, or empty list if suggestions not needed
   */
  private List<String> getSpellingSuggestions(String queryString, PaginatedResult paginatedResult) {

    // Don't suggest if no query provided
    if (queryString == null || queryString.trim().isEmpty()) {
      return Collections.emptyList();
    }

    // Don't suggest if spell checker not available
    if (!suggestionService.isAvailable()) {
      log.debug("Spell checker not available, skipping suggestions");
      return Collections.emptyList();
    }

    // Only suggest if results are poor (5 or fewer hits)
    // Good results suggest the query was correct; suggestions would be noise
    if (paginatedResult.totalHits() > 5) {
      return Collections.emptyList();
    }

    try {
      String[] suggestions = suggestionService.suggestSimilar(queryString, MAX_SUGGESTIONS);
      if (suggestions.length > 0) {
        log.debug("Generated {} spelling suggestions for '{}'", suggestions.length, queryString);
        return Arrays.asList(suggestions);
      }
    } catch (Exception e) {
      log.warn("Failed to generate spelling suggestions for '{}'", queryString, e);
    }

    return Collections.emptyList();
  }

  /**
   * Extracts highlighted snippets from the content field of each search hit.
   *
   * <p>Replaces full document content with context-aware snippets containing query term matches,
   * improving result readability and reducing payload size.
   *
   * @param hits the original search hits with full content fields
   * @param query the Lucene query used for highlighting term matches
   * @return new list of search hits with content replaced by highlighted snippets
   */
  private List<SubmissionSearchHit> extractSnippets(List<SubmissionSearchHit> hits, Query query) {
    return hits.stream().map(hit -> extractSnippet(hit, query)).toList();
  }

  /**
   * Extracts a highlighted snippet from a single search hit's content field.
   *
   * <p>Creates a new immutable SearchHit with the content field replaced by a snippet containing
   * highlighted query terms with surrounding context.
   *
   * @param hit the original search hit with full content
   * @param query the Lucene query for identifying terms to highlight
   * @return a new SearchHit with content replaced by an extracted snippet
   */
  private SubmissionSearchHit extractSnippet(SubmissionSearchHit hit, Query query) {
    String snippet =
        snippetExtractor.extractSnippet(
            query,
            "content", // field name to extract from
            hit.content(),
            true // fragmentOnly - return snippet, not full content
            );

    // Create new immutable SearchHit with snippet
    return new SubmissionSearchHit(
        hit.accession(),
        hit.type(),
        hit.title(),
        hit.author(),
        hit.links(),
        hit.files(),
        hit.releaseDate(),
        hit.views(),
        hit.isPublic(),
        snippet // replaced content
        );
  }

  /**
   * Builds the complete search response DTO from processed components.
   *
   * <p>This method assembles the final response while maintaining backward compatibility with old
   * behavior:
   *
   * <ul>
   *   <li>Query is null when highlighting disabled (browsing mode)
   *   <li>Facets are null when empty (not an empty map)
   *   <li>EFO expanded terms are filtered to only show terms that exist in the index
   *   <li>Synonyms are filtered to only show terms that exist in the index
   *   <li>Spelling suggestions included when results are poor
   * </ul>
   *
   * @param request the original search request
   * @param queryResult the query result with expansion terms and Lucene query
   * @param paginatedResult the paginated search results
   * @param facets the formatted facet dimensions for UI
   * @param hits the search hits with extracted snippets
   * @param suggestions spelling suggestions (empty if not applicable)
   * @return complete search response DTO ready for JSON serialization
   */
  private SearchResponseDTO buildSearchResponse(
      SearchRequest request,
      QueryResult queryResult,
      PaginatedResult<SubmissionSearchHit> paginatedResult,
      List<FacetDimensionDTO> facets,
      List<SubmissionSearchHit> hits,
      List<String> suggestions) {

    // Match old behavior: query is null when highlighting disabled (browsing mode)
    String displayQuery = request.isHighlightingEnabled() ? request.getQuery() : null;

    // Match old behavior: facets null when empty (not empty map)
    Map<String, List<String>> displayFacets =
        (request.getFacets() == null || request.getFacets().isEmpty()) ? null : request.getFacets();

    // Filter EFO terms and synonyms to only show those that actually exist in the index
    // This ensures UI displays honest information grounded in searchable content
    Set<String> filteredEfoTerms =
        filterTermsByIndexPresence(queryResult.getExpandedEfoTerms(), Constants.CONTENT);

    Set<String> filteredSynonyms =
        filterTermsByIndexPresence(queryResult.getExpandedSynonyms(), Constants.CONTENT);

    return new SearchResponseDTO(
        paginatedResult.page(),
        paginatedResult.pageSize(),
        paginatedResult.totalHits(),
        true, // isTotalHitsExact
        request.getSortBy() != null ? request.getSortBy() : RELEVANCE,
        request.getSortOrder() != null ? request.getSortOrder() : "descending",
        suggestions,
        filteredEfoTerms, // Filtered to only existing terms
        filteredSynonyms, // Filtered to only existing terms
        displayQuery, // null when highlighting disabled
        displayFacets, // null when empty
        hits,
        queryResult.getTooManyExpansionTerms());
  }

  /**
   * Builds an error search response for failed search operations.
   *
   * <p>Creates a minimal response with error indication, empty results, and default pagination
   * values. Maintains backward compatibility by setting query to null for browse-all queries
   * ("*:*").
   *
   * @param request the failed search request
   * @param errorMessage the error message to log
   * @return error search response with empty results and default values
   */
  public SearchResponseDTO buildErrorResponse(SearchRequest request, String errorMessage) {
    log.error("Building error response: {}", errorMessage);

    // Match old behavior: "*:*" browse-all query becomes null in response
    String displayQuery = "*:*".equals(request.getQuery()) ? null : request.getQuery();

    return new SearchResponseDTO(
        1, // default first page
        20, // default page size
        0L, // no results
        true, // total is exact (exactly zero)
        RELEVANCE,
        "descending",
        Collections.emptyList(), // no suggestions
        Set.of(), // no expanded EFO terms
        Set.of(), // no expanded synonyms
        displayQuery,
        null, // no facets
        Collections.emptyList(),
        false); // no hits
  }
}
