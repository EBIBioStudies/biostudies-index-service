package uk.ac.ebi.biostudies.index_service.index.management;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.IndexName;

/**
 * Holder container for all Lucene index resources used by the application.
 *
 * <p>This class serves as a centralized holder for sets of Lucene index components, including
 * {@link org.apache.lucene.index.IndexWriter}, {@link org.apache.lucene.index.IndexReader}, and
 * {@link org.apache.lucene.search.IndexSearcher} instances mapped by index name.
 *
 * <p>It provides thread-safe storage and access to these objects and acts as a single source of
 * truth for retrieving them. This class itself does not manage lifecycle events such as opening,
 * closing, or refreshing indices; that responsibility lies elsewhere.
 *
 * <p>By consolidating the available index resources, it simplifies dependency injection and
 * resource sharing across various parts of the application.
 */
@Slf4j
@Component
public class IndexContainer {

  private final Map<String, IndexWriter> indexWriters = new ConcurrentHashMap<>();
  private final Map<String, IndexReader> indexReaders = new ConcurrentHashMap<>();
  private final Map<String, IndexSearcher> indexSearchers = new ConcurrentHashMap<>();

  private final Map<String, SearcherManager> searcherManagers = new ConcurrentHashMap<>();

  /**
   * Retrieves the {@link IndexWriter} for the given index name.
   *
   * @param indexName the {@link IndexName} representing the index
   * @return the {@link IndexWriter} associated with the index
   * @throws IllegalStateException if no {@link IndexWriter} exists for the index
   */
  public IndexWriter getIndexWriter(IndexName indexName) {
    String key = indexName.getIndexName();
    IndexWriter writer = indexWriters.get(key);
    if (writer == null) {
      String errorMessage =
          String.format("Tried to retrieve IndexWriter for index %s, but it does not exist", key);
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
    return writer;
  }

  /**
   * Retrieves the {@link SearcherManager} for the given index name.
   *
   * @param indexName the {@link IndexName} representing the index
   * @return the {@link SearcherManager} associated with the index
   * @throws IllegalStateException if no {@link SearcherManager} exists for the index
   */
  public SearcherManager getSearcherManager(IndexName indexName) {
    String key = indexName.getIndexName();
    SearcherManager searcherManager = searcherManagers.get(key);
    if (searcherManager == null) {
      String errorMessage =
          String.format(
              "Tried to retrieve SearcherManager for index %s, but it does not exist", key);
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
    return searcherManager;
  }

  public void setSearcherManager(IndexName indexName, SearcherManager manager) {
    searcherManagers.put(indexName.getIndexName(), manager);
    log.debug("SearcherManager for {} set", indexName);
  }

  public IndexSearcher acquireSearcher(IndexName indexName) throws IOException {
    return getSearcherManager(indexName).acquire();
  }

  public void releaseSearcher(IndexName indexName, IndexSearcher searcher) throws IOException {
    getSearcherManager(indexName).release(searcher);
  }

  /**
   * Checks whether an {@link IndexWriter} exists for the given index name.
   *
   * @param indexName the {@link IndexName} representing the index
   * @return {@code true} if an {@link IndexWriter} exists; {@code false} otherwise
   */
  public boolean indexWriterExists(IndexName indexName) {
    return indexWriters.containsKey(indexName.getIndexName());
  }

  /**
   * Associates the given {@link IndexWriter} with the specified index name.
   *
   * @param indexName the {@link IndexName} representing the index
   * @param indexWriter the {@link IndexWriter} to store
   */
  public void setIndexWriter(IndexName indexName, IndexWriter indexWriter) {
    indexWriters.put(indexName.getIndexName(), indexWriter);
    log.info("Index writer for index {} set in container", indexName.getIndexName());
  }

  /**
   * Retrieves the {@link IndexReader} for the given index name.
   *
   * @param indexName the {@link IndexName} representing the index
   * @return the {@link IndexReader} associated with the index
   * @throws IllegalStateException if no {@link IndexReader} exists for the index
   */
  @Deprecated
  public IndexReader getIndexReader(IndexName indexName) {
    String key = indexName.getIndexName();
    IndexReader reader = indexReaders.get(key);
    if (reader == null) {
      String errorMessage =
          String.format("Tried to retrieve IndexReader for index %s, but it does not exist", key);
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
    return reader;
  }

  /**
   * Associates the given {@link IndexReader} with the specified index name.
   *
   * @param indexName the {@link IndexName} representing the index
   * @param indexReader the {@link IndexReader} to store
   */
  public void setIndexReader(IndexName indexName, IndexReader indexReader) {
    indexReaders.put(indexName.getIndexName(), indexReader);
    log.info("Index reader for index {} set in container", indexName.getIndexName());
  }

  /**
   * Retrieves the {@link IndexSearcher} for the given index name.
   *
   * @param indexName the {@link IndexName} representing the index
   * @return the {@link IndexSearcher} associated with the index
   * @throws IllegalStateException if no {@link IndexSearcher} exists for the index
   */
  @Deprecated
  public IndexSearcher getIndexSearcher(IndexName indexName) {
    String key = indexName.getIndexName();
    IndexSearcher searcher = indexSearchers.get(key);
    if (searcher == null) {
      String errorMessage =
          String.format("Tried to retrieve IndexSearcher for index %s, but it does not exist", key);
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
    return searcher;
  }

  /**
   * Associates the given {@link IndexSearcher} with the specified index name.
   *
   * @param indexName the {@link IndexName} representing the index
   * @param indexSearcher the {@link IndexSearcher} to store
   */
  public void setIndexSearcher(IndexName indexName, IndexSearcher indexSearcher) {
    indexSearchers.put(indexName.getIndexName(), indexSearcher);
    log.info("Index searcher for index {} set in container", indexName.getIndexName());
  }

  Map<String, SearcherManager> getAllSearcherManagers() {
    return searcherManagers;  // Or unmodifiableView
  }
}
