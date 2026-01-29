package uk.ac.ebi.biostudies.index_service.search.preprocessing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.ac.ebi.biostudies.index_service.search.SearchRequest;

/**
 * Preprocesses and normalizes search requests, applying intelligent defaults and validation rules
 * before query execution.
 */
@Slf4j
@Component
public class QueryPreprocessor {

  private static final String RELEVANCE = "relevance";
  private static final String RELEASE_DATE = "releaseDate";
  private static final String DESCENDING = "descending";
  private static final String ASCENDING = "ascending";

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 1000;

  /**
   * Preprocesses a search request, applying defaults and normalization.
   *
   * @param request the raw search request
   * @return preprocessed search request with defaults applied
   */
  public SearchRequest preprocess(SearchRequest request) {
    log.debug("Preprocessing search request: {}", request);

    SearchRequest processed = new SearchRequest();

    // Normalize query string
    String query = normalizeQuery(request.getQuery());
    processed.setQuery(query);

    // Apply sorting defaults
    applySortingDefaults(request, processed, query);

    // Normalize pagination
    normalizePagination(request, processed);

    // Determine feature flags
    processed.setHighlightingEnabled(shouldEnableHighlighting(query, request.getSortBy()));

    // Copy other fields
    processed.setCollection(request.getCollection());
    processed.setFields(request.getFields());
    processed.setFacets(request.getFacets());

    log.debug("Preprocessed request: {}", processed);
    return processed;
  }

  /** Normalizes the query string, converting empty/null to wildcard. */
  private String normalizeQuery(String query) {
    if (!StringUtils.hasText(query)) {
      return ""; // Empty means "match all" in this context
    }
    return query.trim();
  }

  /**
   * Applies defaults for sorting based on query context.
   *
   * <p>Rules: - Empty query + no sort specified → sort by release date (browsing mode) - Has query
   * + no sort specified → sort by relevance (search mode) - Sort specified → use as-is
   */
  private void applySortingDefaults(SearchRequest request, SearchRequest processed, String query) {
    String sortBy = request.getSortBy();
    String sortOrder = request.getSortOrder();

    boolean isEmptyQuery = query.isEmpty();
    boolean isSortSpecified = StringUtils.hasText(sortBy);

    if (isEmptyQuery && !isSortSpecified) {
      // Browsing mode: sort by newest first
      processed.setSortBy(RELEASE_DATE);
      processed.setSortOrder(DESCENDING);
    } else if (!isSortSpecified) {
      // Search mode: sort by relevance
      processed.setSortBy(RELEVANCE);
      processed.setSortOrder(DESCENDING);
    } else {
      // User specified sort
      processed.setSortBy(sortBy);
      processed.setSortOrder(StringUtils.hasText(sortOrder) ? sortOrder : DESCENDING);
    }
  }

  /** Normalizes and validates pagination parameters. */
  private void normalizePagination(SearchRequest request, SearchRequest processed) {
    // Normalize page (1-based)
    int page =
        request.getPage() != null && request.getPage() > 0 ? request.getPage() : DEFAULT_PAGE;

    // Normalize and limit page size
    int pageSize =
        request.getPageSize() != null && request.getPageSize() > 0
            ? request.getPageSize()
            : DEFAULT_PAGE_SIZE;
    pageSize = Math.min(pageSize, MAX_PAGE_SIZE);

    processed.setPage(page);
    processed.setPageSize(pageSize);
  }

  /**
   * Determines if highlighting should be enabled.
   *
   * <p>Highlighting is disabled for: - Empty queries (no terms to highlight) - Sort-only requests
   * (browsing, not searching)
   */
  private boolean shouldEnableHighlighting(String query, String sortBy) {
    if (query.isEmpty()) {
      return false;
    }

    // If sorting by release date on an empty query, it's browsing mode
    if (RELEASE_DATE.equals(sortBy) && query.isEmpty()) {
      return false;
    }

    return true;
  }
}
