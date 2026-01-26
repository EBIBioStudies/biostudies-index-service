package uk.ac.ebi.biostudies.index_service.search.engine;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Executes Lucene queries with pagination and sorting support across managed indexes. Handles
 * searcher lifecycle via IndexManager's acquire/release pattern.
 */
@Slf4j
@Service
public class LuceneQueryExecutor {

  private static final int MAX_PAGE_SIZE = 1000; // Same as legacy MAX_PAGE_SIZE
  private final IndexManager indexManager;

  public LuceneQueryExecutor(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  /**
   * Executes a Lucene query against the specified index with pagination and optional sorting.
   *
   * @param indexName the index to search
   * @param query the Lucene query to execute
   * @param page the current page number (1-based)
   * @param pageSize the number of results per page
   * @param sort optional sort criteria (null for relevance sorting)
   * @return paginated search results
   * @throws IOException if search execution fails
   */
  public PaginatedResult execute(
      IndexName indexName, Query query, int page, int pageSize, Sort sort) throws IOException {

    IndexSearcher searcher = indexManager.acquireSearcher(indexName);
    try {
      // Limit page size to prevent memory issues
      int limitedPageSize = Math.min(pageSize, MAX_PAGE_SIZE);

      // Calculate total documents needed (all pages up to current page)
      int totalDocsNeeded = Math.min(page * limitedPageSize, Integer.MAX_VALUE);

      // Execute search
      TopDocs topDocs =
          sort != null
              ? searcher.search(query, totalDocsNeeded, sort)
              : searcher.search(query, totalDocsNeeded);

      return new PaginatedResult(topDocs, page, limitedPageSize, topDocs.totalHits.value());

    } finally {
      indexManager.releaseSearcher(indexName, searcher);
    }
  }

  /** Executes a query without sorting (relevance-based). */
  public PaginatedResult execute(IndexName indexName, Query query, int page, int pageSize)
      throws IOException {
    return execute(indexName, query, page, pageSize, null);
  }
}
