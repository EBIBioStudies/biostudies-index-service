package uk.ac.ebi.biostudies.index_service.search.engine;

import java.util.Objects;
import lombok.Getter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;

/**
 * Encapsulates all parameters needed for executing a search query.
 *
 * <p>Provides a fluent builder API for constructing search requests with optional pagination,
 * sorting, and result limiting. Use {@link #of(Query)} for simple non-paginated searches, or {@link
 * Builder} for complex searches with pagination and sorting.
 *
 * <p>Supports three pagination modes:
 *
 * <ul>
 *   <li><b>Offset pagination</b> - Traditional page/pageSize for user-facing pages
 *   <li><b>Search-after pagination</b> - Efficient cursor-based pagination for deep result sets
 *   <li><b>Non-paginated</b> - Returns all results up to a limit
 * </ul>
 *
 * <p>Instances are immutable and thread-safe.
 *
 * <p>Example usage:
 *
 * <pre>
 * // Simple non-paginated search
 * SearchCriteria simple = SearchCriteria.of(query);
 *
 * // Non-paginated search with custom limit
 * SearchCriteria limited = new SearchCriteria.Builder(query)
 *     .limit(500)
 *     .build();
 *
 * // Paginated search with sorting
 * SearchCriteria paginated = new SearchCriteria.Builder(query)
 *     .page(1, 20)
 *     .sort(new Sort(SortField.FIELD_SCORE))
 *     .build();
 *
 * // Search-after for efficient deep pagination
 * SearchCriteria searchAfter = new SearchCriteria.Builder(query)
 *     .sort(sort)
 *     .searchAfter(lastScoreDoc)
 *     .limit(1000)
 *     .build();
 * </pre>
 */
@Getter
public class SearchCriteria {

  private final Query query;
  private final Integer page;
  private final Integer pageSize;
  private final Sort sort;
  private final Integer limit;
  private final ScoreDoc searchAfter;

  /** Private constructor - use {@link #of(Query)} or {@link Builder}. */
  private SearchCriteria(Builder builder) {
    this.query = Objects.requireNonNull(builder.query, "query must not be null");
    this.page = builder.page;
    this.pageSize = builder.pageSize;
    this.sort = builder.sort;
    this.limit = builder.limit;
    this.searchAfter = builder.searchAfter;

    if ((page == null) != (pageSize == null)) {
      throw new IllegalArgumentException(
          "Both page and pageSize must be set together, or both must be null");
    }

    if (page != null && page < 1) {
      throw new IllegalArgumentException("page must be >= 1, got: " + page);
    }

    if (pageSize != null && pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be > 0, got: " + pageSize);
    }

    if (limit != null && limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0, got: " + limit);
    }

    if (isPaginated() && limit != null) {
      throw new IllegalArgumentException(
          "Cannot set both pagination (page/pageSize) and limit. Use pagination for paginated searches, or limit for non-paginated searches.");
    }

    if (searchAfter != null && isPaginated()) {
      throw new IllegalArgumentException(
          "Cannot combine search-after with page-based pagination (page/pageSize). Use one or the other.");
    }

    if (searchAfter != null && sort == null) {
      throw new IllegalArgumentException(
          "search-after requires a Sort to be specified for consistent ordering.");
    }
  }

  /**
   * Creates a simple search criteria with only a query, no pagination, sorting, or limit.
   *
   * @param query the Lucene query to execute
   * @return a new SearchCriteria instance
   * @throws NullPointerException if query is null
   */
  public static SearchCriteria of(Query query) {
    return new Builder(query).build();
  }

  /**
   * Returns true if this search request includes pagination parameters.
   *
   * @return true if both page and pageSize are set, false otherwise
   */
  public boolean isPaginated() {
    return page != null && pageSize != null;
  }

  /**
   * Returns true if this search request uses search-after pagination.
   *
   * <p>Search-after is an efficient alternative to offset pagination for deep result sets, as it
   * doesn't require scoring all previous documents.
   *
   * @return true if searchAfter cursor is set, false otherwise
   */
  public boolean isSearchAfter() {
    return searchAfter != null;
  }

  @Override
  public String toString() {
    return "SearchCriteria{"
        + "query="
        + query
        + ", page="
        + page
        + ", pageSize="
        + pageSize
        + ", sort="
        + sort
        + ", limit="
        + limit
        + ", searchAfter="
        + (searchAfter != null ? "present" : "null")
        + '}';
  }

  /**
   * Builder for constructing {@link SearchCriteria} instances.
   *
   * <p>Provides a fluent API for setting optional pagination, sorting, and limit parameters.
   *
   * <p><b>Note:</b> Pagination (page/pageSize), search-after, and limit have specific compatibility
   * rules:
   *
   * <ul>
   *   <li>Pagination (page/pageSize) and limit are mutually exclusive
   *   <li>Search-after and pagination (page/pageSize) are mutually exclusive
   *   <li>Search-after can be combined with limit
   *   <li>Search-after requires a Sort to be specified
   * </ul>
   */
  public static class Builder {

    private final Query query;
    private Integer page;
    private Integer pageSize;
    private Sort sort;
    private Integer limit;
    private ScoreDoc searchAfter;

    /**
     * Creates a new builder with the specified query.
     *
     * @param query the Lucene query to execute
     * @throws NullPointerException if query is null
     */
    public Builder(Query query) {
      this.query = Objects.requireNonNull(query, "query must not be null");
    }

    /**
     * Sets pagination parameters.
     *
     * <p>Page numbers are 1-indexed (first page is 1, not 0).
     *
     * <p><b>Note:</b> Cannot be used together with {@link #limit(Integer)} or {@link
     * #searchAfter(ScoreDoc)}. Pagination and limit are mutually exclusive, and pagination and
     * search-after are mutually exclusive.
     *
     * @param page the page number, must be >= 1
     * @param pageSize the number of results per page, must be > 0
     * @return this builder for method chaining
     * @throws IllegalArgumentException if page < 1 or pageSize <= 0
     */
    public Builder page(int page, int pageSize) {
      if (page < 1) {
        throw new IllegalArgumentException("page must be >= 1, got: " + page);
      }
      if (pageSize <= 0) {
        throw new IllegalArgumentException("pageSize must be > 0, got: " + pageSize);
      }
      this.page = page;
      this.pageSize = pageSize;
      return this;
    }

    /**
     * Sets custom sorting criteria.
     *
     * <p>If not set, results will be sorted by relevance score (descending).
     *
     * <p><b>Note:</b> Sorting is required when using {@link #searchAfter(ScoreDoc)}.
     *
     * @param sort the sort criteria, may be null for default relevance sorting
     * @return this builder for method chaining
     */
    public Builder sort(Sort sort) {
      this.sort = sort;
      return this;
    }

    /**
     * Sets the maximum number of results to return for non-paginated searches.
     *
     * <p>This is useful when you want all results but with a safety limit lower than the executor's
     * default maximum. If not set, the query executor's default limit applies (typically 10,000).
     *
     * <p><b>Note:</b> Cannot be used together with {@link #page(int, int)}. Limit is only for
     * non-paginated searches. Can be combined with {@link #searchAfter(ScoreDoc)}.
     *
     * @param limit the maximum number of results to return, must be > 0
     * @return this builder for method chaining
     * @throws IllegalArgumentException if limit <= 0
     */
    public Builder limit(Integer limit) {
      if (limit != null && limit <= 0) {
        throw new IllegalArgumentException("limit must be > 0, got: " + limit);
      }
      this.limit = limit;
      return this;
    }

    /**
     * Sets the search-after cursor for efficient deep pagination.
     *
     * <p>Search-after is Lucene's recommended approach for paginating through large result sets.
     * Instead of skipping documents (expensive for deep pages), it resumes from a previous
     * position. This makes it O(pageSize) regardless of depth, unlike offset pagination which is
     * O(page × pageSize).
     *
     * <p><b>Requirements:</b>
     *
     * <ul>
     *   <li>Must provide a {@link Sort} via {@link #sort(Sort)} for consistent ordering
     *   <li>The ScoreDoc must be from a previous search with the same Sort
     *   <li>Cannot be combined with {@link #page(int, int)} (use one or the other)
     *   <li>Can be combined with {@link #limit(Integer)} to control page size
     * </ul>
     *
     * <p><b>Example usage:</b>
     *
     * <pre>
     * // First page
     * SearchCriteria first = new Builder(query)
     *     .sort(sort)
     *     .limit(1000)
     *     .build();
     * PaginatedResult&lt;T&gt; page1 = searcher.search(first);
     *
     * // Next page using search-after
     * SearchCriteria next = new Builder(query)
     *     .sort(sort)
     *     .limit(1000)
     *     .searchAfter(page1.lastScoreDoc())
     *     .build();
     * PaginatedResult&lt;T&gt; page2 = searcher.search(next);
     * </pre>
     *
     * @param searchAfter the last ScoreDoc from the previous page, or null to disable
     * @return this builder for method chaining
     */
    public Builder searchAfter(ScoreDoc searchAfter) {
      this.searchAfter = searchAfter;
      return this;
    }

    /**
     * Builds the {@link SearchCriteria} instance.
     *
     * @return a new SearchCriteria instance
     * @throws IllegalArgumentException if validation fails, or if incompatible parameters are set
     */
    public SearchCriteria build() {
      return new SearchCriteria(this);
    }
  }
}
