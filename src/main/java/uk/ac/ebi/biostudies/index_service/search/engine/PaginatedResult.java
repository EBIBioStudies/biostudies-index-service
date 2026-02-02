package uk.ac.ebi.biostudies.index_service.search.engine;

import java.util.List;
import java.util.function.Function;

/**
 * Represents a paginated search result containing a subset of results and pagination metadata.
 *
 * @param <T> the type of objects in the result list
 * @param results the list of results for the current page
 * @param page the current page number (zero-based)
 * @param pageSize the number of results per page
 * @param totalHits the total number of matching documents
 * @param isTotalHitsExact whether the total hit count is exact or an approximation
 */
public record PaginatedResult<T>(
    List<T> results, int page, int pageSize, long totalHits, boolean isTotalHitsExact) {

  /**
   * Calculates the total number of pages based on total hits and page size.
   *
   * @return the total number of pages, at least 1 even if there are no results
   */
  public int getTotalPages() {
    if (totalHits == 0) {
      return 1;
    }
    return (int) Math.ceil((double) totalHits / pageSize);
  }

  /**
   * Checks if there is a next page available.
   *
   * @return true if there are more results beyond the current page
   */
  public boolean hasNextPage() {
    return page < getTotalPages();
  }

  /**
   * Checks if there is a previous page available.
   *
   * @return true if the current page is not the first page
   */
  public boolean hasPreviousPage() {
    return page > 1;
  }

  /**
   * Checks if the current page is empty (contains no documents).
   *
   * @return true if documents list is empty
   */
  public boolean isEmpty() {
    return results.isEmpty();
  }

  /**
   * Transforms this paginated result by applying a mapping function to each result.
   *
   * <p>Useful for converting between different types while preserving pagination metadata.
   *
   * @param mapper the function to apply to each result
   * @param <R> the type of the transformed results
   * @return a new PaginatedResult with transformed results
   */
  public <R> PaginatedResult<R> map(Function<T, R> mapper) {
    return new PaginatedResult<>(
        results.stream().map(mapper).toList(), page, pageSize, totalHits, isTotalHitsExact);
  }
}
