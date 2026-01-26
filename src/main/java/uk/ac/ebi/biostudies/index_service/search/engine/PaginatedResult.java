package uk.ac.ebi.biostudies.index_service.search.engine;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;

/**
 * Wrapper for paginated Lucene search results.
 *
 * @param topDocs the Lucene TopDocs containing scored search results for ALL pages up to current
 * @param page the current page number (1-based)
 * @param pageSize the number of results per page
 * @param totalHits the total number of matching documents
 */
public record PaginatedResult(TopDocs topDocs, int page, int pageSize, long totalHits) {

  /** Returns whether the total hits count is exact or an estimate. */
  public boolean isTotalHitsExact() {
    return topDocs.totalHits.relation() == TotalHits.Relation.EQUAL_TO;
  }

  /** Gets the start index for the current page in the ScoreDoc array. */
  public int getStartIndex() {
    return (page - 1) * pageSize;
  }

  /** Gets the end index (exclusive) for the current page in the ScoreDoc array. */
  public int getEndIndex() {
    long end = (long) page * pageSize;
    return (int) Math.min(end, topDocs.scoreDocs.length);
  }

  /** Gets the ScoreDoc array for only the current page. */
  public ScoreDoc[] getCurrentPageDocs() {
    int start = getStartIndex();
    int end = getEndIndex();
    if (start >= topDocs.scoreDocs.length) {
      return new ScoreDoc[0];
    }
    ScoreDoc[] pageDocs = new ScoreDoc[end - start];
    System.arraycopy(topDocs.scoreDocs, start, pageDocs, 0, end - start);
    return pageDocs;
  }
}
