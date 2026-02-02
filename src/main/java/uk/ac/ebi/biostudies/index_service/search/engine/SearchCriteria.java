package uk.ac.ebi.biostudies.index_service.search.engine;

import java.util.Objects;
import lombok.Getter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;

/**
 * Encapsulates all parameters needed for executing a search query.
 *
 * <p>Provides a fluent builder API for constructing search requests with optional pagination and
 * sorting. Use {@link #of(Query)} for simple non-paginated searches, or {@link Builder} for complex
 * searches with pagination and sorting.
 *
 * <p>Instances are immutable and thread-safe.
 *
 * <p>Example usage:
 *
 * <pre>
 * // Simple search
 * SearchCriteria simple = SearchCriteria.of(query);
 *
 * // Paginated search with sorting
 * SearchCriteria complex = new SearchCriteria.Builder(query)
 *     .page(1, 20)
 *     .sort(new Sort(SortField.FIELD_SCORE))
 *     .build();
 * </pre>
 */
@Getter
public class SearchCriteria {

  private final Query query;
  private final Integer page;
  private final Integer pageSize;
  private final Sort sort;

  /** Private constructor - use {@link #of(Query)} or {@link Builder}. */
  private SearchCriteria(Builder builder) {
    this.query = Objects.requireNonNull(builder.query, "query must not be null");
    this.page = builder.page;
    this.pageSize = builder.pageSize;
    this.sort = builder.sort;

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
  }

  /**
   * Creates a simple search criteria with only a query, no pagination or sorting.
   *
   * @param query the Lucene query to execute
   * @return a new SearchCriteria instance
   * @throws NullPointerException if query is null
   */
  public static SearchCriteria of(Query query) {
    return new Builder(query).build();
  }

  /**
   * Returns the Lucene query to execute.
   *
   * @return the query, never null
   */
  public Query getQuery() {
    return query;
  }

  /**
   * Returns the requested page number (1-indexed), or null if pagination is not requested.
   *
   * @return the page number, or null for non-paginated searches
   */
  public Integer getPage() {
    return page;
  }

  /**
   * Returns the number of results per page, or null if pagination is not requested.
   *
   * @return the page size, or null for non-paginated searches
   */
  public Integer getPageSize() {
    return pageSize;
  }

  /**
   * Returns the sort criteria, or null for default relevance-based sorting.
   *
   * @return the sort criteria, or null
   */
  public Sort getSort() {
    return sort;
  }

  /**
   * Returns true if this search request includes pagination parameters.
   *
   * @return true if both page and pageSize are set, false otherwise
   */
  public boolean isPaginated() {
    return page != null && pageSize != null;
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
        + '}';
  }

  /**
   * Builder for constructing {@link SearchCriteria} instances.
   *
   * <p>Provides a fluent API for setting optional pagination and sorting parameters.
   */
  public static class Builder {

    private final Query query;
    private Integer page;
    private Integer pageSize;
    private Sort sort;

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
     * @param sort the sort criteria, may be null for default relevance sorting
     * @return this builder for method chaining
     */
    public Builder sort(Sort sort) {
      this.sort = sort;
      return this;
    }

    /**
     * Builds the {@link SearchCriteria} instance.
     *
     * @return a new SearchCriteria instance
     * @throws IllegalArgumentException if validation fails
     */
    public SearchCriteria build() {
      return new SearchCriteria(this);
    }
  }
}
