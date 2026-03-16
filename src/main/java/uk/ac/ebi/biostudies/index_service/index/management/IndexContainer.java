package uk.ac.ebi.biostudies.index_service.index.management;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.IndexName;

/**
 * Centralized holder for all active Lucene index resources.
 *
 * <p>Stores {@link IndexWriter} and {@link SearcherManager} instances keyed by index name. All
 * search access goes through {@link SearcherManager#acquire()} and {@link
 * SearcherManager#release(IndexSearcher)} — never through a stored {@link IndexSearcher} directly,
 * as those go stale after the first refresh.
 *
 * <p>This class does not manage lifecycle (opening, closing, refreshing). That responsibility
 * belongs to {@link IndexManager}.
 */
@Slf4j
@Component
public class IndexContainer {

  private final Map<String, IndexWriter> indexWriters = new ConcurrentHashMap<>();
  private final Map<String, SearcherManager> searcherManagers = new ConcurrentHashMap<>();

  // -------------------------------------------------------------------------
  // IndexWriter
  // -------------------------------------------------------------------------

  /**
   * Returns the {@link IndexWriter} for the given index.
   *
   * @throws IllegalStateException if no writer has been registered for this index
   */
  public IndexWriter getIndexWriter(IndexName indexName) {
    String key = indexName.getIndexName();
    IndexWriter writer = indexWriters.get(key);
    if (writer == null) {
      throw new IllegalStateException(
          "No IndexWriter registered for index: "
              + key
              + ". This method must only be called from the writer role.");
    }
    return writer;
  }

  /**
   * Registers an {@link IndexWriter} for the given index. Replaces any previously registered writer
   * without closing it — closing is the caller's responsibility.
   */
  public void setIndexWriter(IndexName indexName, IndexWriter writer) {
    indexWriters.put(indexName.getIndexName(), writer);
    log.info("IndexWriter registered for index {}", indexName.getIndexName());
  }

  /** Returns {@code true} if a writer has been registered for this index. */
  public boolean indexWriterExists(IndexName indexName) {
    return indexWriters.containsKey(indexName.getIndexName());
  }

  /**
   * Returns an unmodifiable view of all registered writers. Used by {@link IndexManager#closeAll()}
   * to flush and release write locks.
   */
  public Map<String, IndexWriter> getAllIndexWriters() {
    return Collections.unmodifiableMap(indexWriters);
  }

  // -------------------------------------------------------------------------
  // SearcherManager — the correct path for all search access
  // -------------------------------------------------------------------------

  /**
   * Returns the {@link SearcherManager} for the given index.
   *
   * @throws IllegalStateException if no manager has been registered for this index
   */
  public SearcherManager getSearcherManager(IndexName indexName) {
    String key = indexName.getIndexName();
    SearcherManager manager = searcherManagers.get(key);
    if (manager == null) {
      throw new IllegalStateException("No SearcherManager registered for index: " + key);
    }
    return manager;
  }

  /**
   * Registers a {@link SearcherManager} for the given index. Replaces any previously registered
   * manager without closing it — closing is the caller's responsibility.
   */
  public void setSearcherManager(IndexName indexName, SearcherManager manager) {
    searcherManagers.put(indexName.getIndexName(), manager);
    log.info("SearcherManager registered for index {}", indexName.getIndexName());
  }

  /**
   * Acquires an {@link IndexSearcher} from the {@link SearcherManager} for the given index. Callers
   * must release the searcher via {@link #releaseSearcher} when done, even if an exception is
   * thrown — use try/finally.
   *
   * @throws IOException if the manager fails to acquire a searcher
   * @throws IllegalStateException if no manager exists for this index
   */
  public IndexSearcher acquireSearcher(IndexName indexName) throws IOException {
    return getSearcherManager(indexName).acquire();
  }

  /**
   * Releases an {@link IndexSearcher} back to its {@link SearcherManager}. Must be called in a
   * {@code finally} block paired with {@link #acquireSearcher}.
   *
   * @throws IOException if the release fails
   */
  public void releaseSearcher(IndexName indexName, IndexSearcher searcher) throws IOException {
    getSearcherManager(indexName).release(searcher);
  }

  /**
   * Returns an unmodifiable view of all registered {@link SearcherManager} instances. Used by
   * {@link IndexManager} for bulk refresh and shutdown.
   */
  public Map<String, SearcherManager> getAllSearcherManagers() {
    return Collections.unmodifiableMap(searcherManagers);
  }
}
