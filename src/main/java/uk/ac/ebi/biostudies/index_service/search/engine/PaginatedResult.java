package uk.ac.ebi.biostudies.index_service.search.engine;

import java.util.List;
import org.apache.lucene.search.ScoreDoc;

/**
 * Represents a paginated result set with metadata.
 *
 * @param results the list of results for the current page
 * @param page the current page number (1-based for offset pagination, 0 for search-after)
 * @param pageSize the number of items per page
 * @param totalHits the total number of matching documents
 * @param isTotalHitsExact whether totalHits is an exact count or lower bound
 * @param lastScoreDoc the last ScoreDoc for search-after continuation (may be null)
 */
public record PaginatedResult<T>(
    List<T> results,
    int page,
    int pageSize,
    long totalHits,
    boolean isTotalHitsExact,
    ScoreDoc lastScoreDoc) {

  /** Constructor for backward compatibility (without lastScoreDoc). */
  public PaginatedResult(
      List<T> results, int page, int pageSize, long totalHits, boolean isTotalHitsExact) {
    this(results, page, pageSize, totalHits, isTotalHitsExact, null);
  }

  /** Maps the results to a different type while preserving pagination metadata. */
  public <R> PaginatedResult<R> map(java.util.function.Function<T, R> mapper) {
    List<R> mappedResults = results.stream().map(mapper).toList();
    return new PaginatedResult<>(
        mappedResults, page, pageSize, totalHits, isTotalHitsExact, lastScoreDoc);
  }
}
