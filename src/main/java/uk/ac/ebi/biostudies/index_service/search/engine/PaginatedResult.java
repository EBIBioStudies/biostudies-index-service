package uk.ac.ebi.biostudies.index_service.search.engine;

import java.util.List;
import uk.ac.ebi.biostudies.index_service.search.SearchHit;

/**
 * Represents a page of search results with pagination metadata.
 * <p>
 * This record contains the fully-hydrated search hits for the current page only,
 * along with metadata needed for pagination and navigation. All search hits are
 * complete domain objects ready for serialization to JSON responses.
 * </p>
 *
 * @param hits the list of {@link SearchHit} objects for the current page only
 * @param page the current page number (1-based)
 * @param pageSize the number of results per page
 * @param totalHits the total number of matching documents across all pages
 * @param isTotalHitsExact whether the total hits count is exact or an estimate
 */
public record PaginatedResult(
    List<SearchHit> hits,
    int page,
    int pageSize,
    long totalHits,
    boolean isTotalHitsExact) {

  /**
   * Calculates the total number of pages based on total hits and page size.
   *
   * @return the total number of pages
   */
  public int getTotalPages() {
    return (int) Math.ceil((double) totalHits / pageSize);
  }

  /**
   * Checks if there is a next page available.
   *
   * @return true if more results exist beyond the current page
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
}
