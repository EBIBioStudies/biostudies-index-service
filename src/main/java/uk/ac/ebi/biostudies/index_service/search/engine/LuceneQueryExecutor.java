package uk.ac.ebi.biostudies.index_service.search.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Generic executor for Lucene queries that returns raw {@link Document} instances rather than
 * domain-specific objects. Provides both paginated and non-paginated query execution with support
 * for custom sorting.
 *
 * <p>This service manages the searcher lifecycle through {@link IndexManager}'s acquire/release
 * pattern, ensuring proper resource management across concurrent searches. The {@link IndexManager}
 * uses Lucene's {@code SearcherManager} internally, making this executor thread-safe.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe and can be safely used by multiple threads
 * concurrently. Each query execution acquires its own searcher instance from the IndexManager.
 *
 * <p><b>Resource Management:</b> All searcher instances are automatically released via try-finally
 * blocks, even when exceptions occur.
 *
 * <p><b>Pagination Limits:</b> Page size is limited to {@value #MAX_PAGE_SIZE} to prevent memory
 * exhaustion. Requests exceeding this limit will throw {@link IllegalArgumentException}.
 *
 * <p>Example usage:
 *
 * <pre>
 * SearchCriteria criteria = new SearchCriteria.Builder(query)
 *     .page(1, 20)
 *     .build();
 * PaginatedResult&lt;Document&gt; result = executor.execute(IndexName.BIOSTUDIES, criteria);
 * List&lt;Document&gt; docs = result.results();
 * long totalHits = result.totalHits();
 * </pre>
 *
 * @see IndexManager
 * @see PaginatedResult
 * @see SearchCriteria
 */
@Slf4j
@Service
public class LuceneQueryExecutor {

  /** Maximum allowed page size to prevent memory exhaustion from excessively large result sets. */
  private static final int MAX_PAGE_SIZE = 1000;

  /**
   * Default number of results to fetch when retrieving all matching documents without pagination.
   * Set conservatively to avoid OutOfMemoryError on large indexes.
   */
  private static final int DEFAULT_MAX_RESULTS = 10000;

  /**
   * Maximum number of documents to score for deep pagination. Limits memory usage and prevents
   * performance degradation on very deep pages.
   */
  private static final int MAX_TOTAL_DOCS_FOR_PAGINATION = 50000;

  private final IndexManager indexManager;

  /**
   * Constructs a new query executor.
   *
   * @param indexManager the index manager responsible for searcher lifecycle management
   * @throws NullPointerException if indexManager is null
   */
  public LuceneQueryExecutor(IndexManager indexManager) {
    this.indexManager = Objects.requireNonNull(indexManager, "indexManager must not be null");
  }

  /**
   * Executes a search based on the provided criteria.
   *
   * <p>If the criteria includes pagination, returns a specific page of results. Otherwise, returns
   * all matching documents up to {@value #DEFAULT_MAX_RESULTS}.
   *
   * <p><b>Performance note:</b> Lucene must internally score all documents up to the requested page
   * to maintain score/sort order. For deep pagination needs (page * pageSize &gt; {@value
   * #MAX_TOTAL_DOCS_FOR_PAGINATION}), consider using Lucene's search-after API instead.
   *
   * @param indexName the name of the index to search against
   * @param criteria the search criteria including query, pagination, and sorting options
   * @return a {@link PaginatedResult} containing documents, total hits, and pagination metadata
   * @throws IOException if an I/O error occurs during search
   * @throws IllegalArgumentException if pageSize &gt; {@value #MAX_PAGE_SIZE}, or page * pageSize
   *     &gt; {@value #MAX_TOTAL_DOCS_FOR_PAGINATION}
   * @throws NullPointerException if indexName or criteria is null
   */
  public PaginatedResult<Document> execute(IndexName indexName, SearchCriteria criteria)
      throws IOException {
    Objects.requireNonNull(indexName, "indexName must not be null");
    Objects.requireNonNull(criteria, "criteria must not be null");

    if (criteria.isPaginated()) {
      return executePaginated(indexName, criteria);
    } else {
      return executeNonPaginated(indexName, criteria);
    }
  }

  /** Executes a paginated search. */
  private PaginatedResult<Document> executePaginated(IndexName indexName, SearchCriteria criteria)
      throws IOException {

    int page = criteria.getPage();
    int pageSize = criteria.getPageSize();
    Query query = criteria.getQuery();
    Sort sort = criteria.getSort();

    if (pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "pageSize exceeds maximum allowed size. Requested: "
              + pageSize
              + ", Maximum: "
              + MAX_PAGE_SIZE);
    }

    int totalDocsNeeded = page * pageSize;
    if (totalDocsNeeded > MAX_TOTAL_DOCS_FOR_PAGINATION) {
      throw new IllegalArgumentException(
          "Deep pagination limit exceeded. Requested page "
              + page
              + " with pageSize "
              + pageSize
              + " requires scoring "
              + totalDocsNeeded
              + " documents, which exceeds maximum "
              + MAX_TOTAL_DOCS_FOR_PAGINATION
              + ". Consider using search-after pagination for deep result sets.");
    }

    IndexSearcher searcher = indexManager.acquireSearcher(indexName);
    try {
      TopDocs topDocs =
          sort != null
              ? searcher.search(query, totalDocsNeeded, sort)
              : searcher.search(query, totalDocsNeeded);

      int start = (page - 1) * pageSize;

      // Requested page is beyond available hits -> return empty page (but keep totalHits metadata)
      if (start >= topDocs.scoreDocs.length) {
        boolean isTotalHitsExact = topDocs.totalHits.relation() == TotalHits.Relation.EQUAL_TO;

        return new PaginatedResult<>(
            List.of(), page, pageSize, topDocs.totalHits.value(), isTotalHitsExact);
      }

      int end = Math.min(start + pageSize, topDocs.scoreDocs.length);

      List<Document> documents = new ArrayList<>(Math.max(0, end - start));
      StoredFields storedFields = searcher.storedFields();

      for (int docIndex = start; docIndex < end; docIndex++) {
        Document doc = storedFields.document(topDocs.scoreDocs[docIndex].doc);
        documents.add(doc);
      }

      boolean isTotalHitsExact = topDocs.totalHits.relation() == TotalHits.Relation.EQUAL_TO;

      log.debug(
          "Query executed on index={}, page={}, pageSize={}, totalHits={}, exact={}",
          indexName,
          page,
          pageSize,
          topDocs.totalHits.value(),
          isTotalHitsExact);

      return new PaginatedResult<>(
          documents, page, pageSize, topDocs.totalHits.value(), isTotalHitsExact);

    } finally {
      indexManager.releaseSearcher(indexName, searcher);
    }
  }

  /** Executes a non-paginated search, returning all results up to {@value #DEFAULT_MAX_RESULTS}. */
  private PaginatedResult<Document> executeNonPaginated(
      IndexName indexName, SearchCriteria criteria) throws IOException {

    Query query = criteria.getQuery();
    Sort sort = criteria.getSort();

    IndexSearcher searcher = indexManager.acquireSearcher(indexName);
    try {
      TopDocs topDocs =
          sort != null
              ? searcher.search(query, DEFAULT_MAX_RESULTS, sort)
              : searcher.search(query, DEFAULT_MAX_RESULTS);

      if (topDocs.totalHits.value() > DEFAULT_MAX_RESULTS) {
        log.warn(
            "Query on index {} returned {} hits but only fetching top {}. Consider using pagination.",
            indexName,
            topDocs.totalHits.value(),
            DEFAULT_MAX_RESULTS);
      }

      List<Document> documents = new ArrayList<>(topDocs.scoreDocs.length);
      StoredFields storedFields = searcher.storedFields();

      for (int docIndex = 0; docIndex < topDocs.scoreDocs.length; docIndex++) {
        Document doc = storedFields.document(topDocs.scoreDocs[docIndex].doc);
        documents.add(doc);
      }

      boolean isTotalHitsExact = topDocs.totalHits.relation() == TotalHits.Relation.EQUAL_TO;

      log.debug(
          "Non-paginated query executed on index={}, returned {} of {} total hits",
          indexName,
          documents.size(),
          topDocs.totalHits.value());

      return new PaginatedResult<>(
          documents, 1, documents.size(), topDocs.totalHits.value(), isTotalHitsExact);

    } finally {
      indexManager.releaseSearcher(indexName, searcher);
    }
  }
}
