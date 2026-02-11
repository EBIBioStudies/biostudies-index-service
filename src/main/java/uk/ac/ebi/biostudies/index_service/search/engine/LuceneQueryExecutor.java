package uk.ac.ebi.biostudies.index_service.search.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Generic executor for Lucene queries that returns raw {@link Document} instances rather than
 * domain-specific objects. Provides paginated, non-paginated, and search-after query execution with
 * support for custom sorting.
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
 * <p><b>Pagination Modes:</b>
 *
 * <ul>
 *   <li><b>Offset pagination</b> - Traditional page/pageSize, limited to {@value
 *       #MAX_TOTAL_DOCS_FOR_PAGINATION} scored documents
 *   <li><b>Search-after pagination</b> - Efficient cursor-based pagination for deep result sets, no
 *       depth limits
 *   <li><b>Non-paginated</b> - Returns all results up to {@value #DEFAULT_MAX_RESULTS}
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>
 * // Offset pagination
 * SearchCriteria criteria = new SearchCriteria.Builder(query)
 *     .page(1, 20)
 *     .build();
 * PaginatedResult&lt;Document&gt; result = executor.execute(IndexName.BIOSTUDIES, criteria);
 *
 * // Search-after pagination (for deep results)
 * SearchCriteria searchAfter = new SearchCriteria.Builder(query)
 *     .sort(sort)
 *     .searchAfter(lastScoreDoc)
 *     .limit(1000)
 *     .build();
 * PaginatedResult&lt;Document&gt; nextPage = executor.execute(IndexName.BIOSTUDIES, searchAfter);
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
   * performance degradation on very deep pages. Does not apply to search-after pagination.
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
   * <p>Supports three execution modes:
   *
   * <ul>
   *   <li><b>Search-after</b> - Efficient deep pagination using cursor (no depth limit)
   *   <li><b>Offset pagination</b> - Traditional page/pageSize (limited to {@value
   *       #MAX_TOTAL_DOCS_FOR_PAGINATION})
   *   <li><b>Non-paginated</b> - Returns all results up to {@value #DEFAULT_MAX_RESULTS}
   * </ul>
   *
   * <p><b>Performance note:</b> For deep pagination (page * pageSize &gt; {@value
   * #MAX_TOTAL_DOCS_FOR_PAGINATION}), use search-after mode via {@link
   * SearchCriteria.Builder#searchAfter(ScoreDoc)}.
   *
   * @param indexName the name of the index to search against
   * @param criteria the search criteria including query, pagination, and sorting options
   * @return a {@link PaginatedResult} containing documents, total hits, and pagination metadata
   * @throws IOException if an I/O error occurs during search
   * @throws IllegalArgumentException if pageSize &gt; {@value #MAX_PAGE_SIZE}, or page * pageSize
   *     &gt; {@value #MAX_TOTAL_DOCS_FOR_PAGINATION} (for offset pagination only)
   * @throws NullPointerException if indexName or criteria is null
   */
  public PaginatedResult<Document> execute(IndexName indexName, SearchCriteria criteria)
      throws IOException {
    Objects.requireNonNull(indexName, "indexName must not be null");
    Objects.requireNonNull(criteria, "criteria must not be null");

    if (criteria.isSearchAfter()) {
      return executeSearchAfter(indexName, criteria);
    } else if (criteria.isPaginated()) {
      return executePaginated(indexName, criteria);
    } else {
      return executeNonPaginated(indexName, criteria);
    }
  }

  /**
   * Executes a search-after pagination query.
   *
   * <p>Search-after is Lucene's efficient approach for paginating through large result sets. Unlike
   * offset pagination, it doesn't require scoring all previous documents, making it O(pageSize)
   * regardless of depth.
   *
   * <p><b>Advantages over offset pagination:</b>
   *
   * <ul>
   *   <li>No depth limit - can paginate through millions of results
   *   <li>Constant performance - doesn't slow down for deep pages
   *   <li>Lower memory usage - only scores requested page
   * </ul>
   *
   * @param indexName the index to search
   * @param criteria search criteria with searchAfter cursor and sort
   * @return paginated results after the cursor position
   * @throws IOException if search fails
   */
  private PaginatedResult<Document> executeSearchAfter(IndexName indexName, SearchCriteria criteria)
      throws IOException {

    Query query = criteria.getQuery();
    Sort sort = criteria.getSort(); // Required for search-after (validated in SearchCriteria)
    ScoreDoc searchAfter = criteria.getSearchAfter();

    // Determine page size from limit or use default
    int pageSize =
        criteria.getLimit() != null ? Math.min(criteria.getLimit(), MAX_PAGE_SIZE) : MAX_PAGE_SIZE;

    IndexSearcher searcher = indexManager.acquireSearcher(indexName);
    try {
      // Use searchAfter for efficient deep pagination
      TopDocs topDocs = searcher.searchAfter(searchAfter, query, pageSize, sort);

      List<Document> documents = new ArrayList<>(topDocs.scoreDocs.length);
      StoredFields storedFields = searcher.storedFields();

      for (int docIndex = 0; docIndex < topDocs.scoreDocs.length; docIndex++) {
        Document doc = storedFields.document(topDocs.scoreDocs[docIndex].doc);
        documents.add(doc);
      }

      boolean isTotalHitsExact = topDocs.totalHits.relation() == TotalHits.Relation.EQUAL_TO;

      // Get last ScoreDoc for continuation (null if no results)
      ScoreDoc lastScoreDoc =
          topDocs.scoreDocs.length > 0 ? topDocs.scoreDocs[topDocs.scoreDocs.length - 1] : null;

      log.debug(
          "Search-after query executed on index={}, returned {} documents, totalHits={}, exact={}, hasMore={}",
          indexName,
          documents.size(),
          topDocs.totalHits.value(),
          isTotalHitsExact,
          lastScoreDoc != null);

      // For search-after, page number is 0 (cursor-based, not page-based)
      return new PaginatedResult<>(
          documents,
          0, // page number not applicable for search-after
          pageSize,
          topDocs.totalHits.value(),
          isTotalHitsExact,
          lastScoreDoc);

    } finally {
      indexManager.releaseSearcher(indexName, searcher);
    }
  }

  /** Executes a paginated search using offset-based pagination. */
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
            List.of(), page, pageSize, topDocs.totalHits.value(), isTotalHitsExact, null);
      }

      int end = Math.min(start + pageSize, topDocs.scoreDocs.length);

      List<Document> documents = new ArrayList<>(Math.max(0, end - start));
      StoredFields storedFields = searcher.storedFields();

      for (int docIndex = start; docIndex < end; docIndex++) {
        Document doc = storedFields.document(topDocs.scoreDocs[docIndex].doc);
        documents.add(doc);
      }

      boolean isTotalHitsExact = topDocs.totalHits.relation() == TotalHits.Relation.EQUAL_TO;

      // Include last ScoreDoc for potential search-after continuation
      ScoreDoc lastScoreDoc = end > 0 ? topDocs.scoreDocs[end - 1] : null;

      log.debug(
          "Offset pagination query executed on index={}, page={}, pageSize={}, totalHits={}, exact={}",
          indexName,
          page,
          pageSize,
          topDocs.totalHits.value(),
          isTotalHitsExact);

      return new PaginatedResult<>(
          documents, page, pageSize, topDocs.totalHits.value(), isTotalHitsExact, lastScoreDoc);

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
    int maxNumberResults =
        criteria.getLimit() != null
            ? Math.min(criteria.getLimit(), DEFAULT_MAX_RESULTS)
            : DEFAULT_MAX_RESULTS;
    try {
      TopDocs topDocs =
          sort != null
              ? searcher.search(query, maxNumberResults, sort)
              : searcher.search(query, maxNumberResults);

      if (topDocs.totalHits.value() > DEFAULT_MAX_RESULTS) {
        log.warn(
            "Query on index {} returned {} hits but only fetching top {}. Consider using pagination.",
            indexName,
            topDocs.totalHits.value(),
            maxNumberResults);
      }

      List<Document> documents = new ArrayList<>(topDocs.scoreDocs.length);
      StoredFields storedFields = searcher.storedFields();

      for (int docIndex = 0; docIndex < topDocs.scoreDocs.length; docIndex++) {
        Document doc = storedFields.document(topDocs.scoreDocs[docIndex].doc);
        documents.add(doc);
      }

      boolean isTotalHitsExact = topDocs.totalHits.relation() == TotalHits.Relation.EQUAL_TO;

      // Include last ScoreDoc for potential search-after continuation
      ScoreDoc lastScoreDoc =
          topDocs.scoreDocs.length > 0 ? topDocs.scoreDocs[topDocs.scoreDocs.length - 1] : null;

      log.debug(
          "Non-paginated query executed on index={}, returned {} of {} total hits",
          indexName,
          documents.size(),
          topDocs.totalHits.value());

      return new PaginatedResult<>(
          documents,
          1,
          documents.size(),
          topDocs.totalHits.value(),
          isTotalHitsExact,
          lastScoreDoc);

    } finally {
      indexManager.releaseSearcher(indexName, searcher);
    }
  }

  /**
   * Gets the document frequency for a specific term in a field.
   *
   * <p>Document frequency is the number of documents that contain the term in the specified field.
   * This is useful for filtering autocomplete suggestions, spell checking, and relevance scoring.
   *
   * <p><strong>Performance:</strong> This is a fast O(1) operation using Lucene's term dictionary
   * (inverted index lookup). It does not scan documents.
   *
   * <p><strong>Thread Safety:</strong> Uses acquireSearcher/releaseSearcher pattern for safe
   * concurrent access to the index.
   *
   * @param field the field name to search in (e.g., "content", "title")
   * @param term the term text to check (will be lowercased for matching)
   * @param indexName the index to query (SUBMISSION, EFO, etc.)
   * @return the number of documents containing the term, or 0 if term not found or invalid inputs
   * @throws IOException if there's an error accessing the index
   */
  public int getTermFrequency(String field, String term, IndexName indexName) throws IOException {
    if (field == null || field.isEmpty()) {
      log.warn("Empty field name provided for term frequency calculation");
      return 0;
    }
    if (term == null || term.isEmpty()) {
      log.warn("Empty term provided for term frequency calculation");
      return 0;
    }

    IndexSearcher searcher = null;
    try {
      searcher = indexManager.acquireSearcher(indexName);
      IndexReader reader = searcher.getIndexReader();

      // Check if term exists in the index with at least 1 document
      Term luceneTerm = new Term(field, term.toLowerCase());
      int frequency = reader.docFreq(luceneTerm);

      log.trace("Term '{}' in field '{}' appears in {} documents", term, field, frequency);

      return frequency;

    } finally {
      if (searcher != null) {
        indexManager.releaseSearcher(indexName, searcher);
      }
    }
  }
}
