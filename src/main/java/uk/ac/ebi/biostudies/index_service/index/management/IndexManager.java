package uk.ac.ebi.biostudies.index_service.index.management;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.analysis.AnalyzerManager;
import uk.ac.ebi.biostudies.index_service.config.AppRoleConfig;
import uk.ac.ebi.biostudies.index_service.index.IndexName;

/**
 * Manages Lucene index resources including writers, readers, and searchers.
 *
 * <p>Behaviour is split by role:
 *
 * <ul>
 *   <li><b>Writer role</b>: opens a full NRT stack (IndexWriter + SearcherManager +
 *       ControlledRealTimeReopenThread) against local NVMe storage.
 *   <li><b>Reader role</b>: opens a lightweight read-only stack (SearcherManager) against the NFS
 *       snapshot directory, refreshed via {@link DirectoryReader#openIfChanged} on a fixed poll
 *       schedule.
 * </ul>
 */
@Slf4j
@Component
public class IndexManager {

  // Writer-role NRT staleness bounds
  private static final double TARGET_MAX_STALE_SEC = 5.0;
  private static final double TARGET_MIN_STALE_SEC = 0.1;

  // Reader-role poll interval
  private static final long READER_POLL_INTERVAL_SEC = 5;

  private final Map<String, Long> lastSeenGeneration = new ConcurrentHashMap<>();

  private final IndexContainer container;
  private final AnalyzerManager analyzerManager;
  private final AppRoleConfig roleConfig;

  /** NRT reopen threads — writer role only, one per index. */
  private final Map<String, ControlledRealTimeReopenThread<IndexSearcher>> reopenThreads =
      new ConcurrentHashMap<>();

  /**
   * Single-thread scheduler shared across all reader-role poll tasks. Named thread makes it easy to
   * spot in thread dumps.
   */
  private final ScheduledExecutorService readerPollScheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "index-reader-poll");
            t.setDaemon(true);
            return t;
          });

  /** Cancellable poll futures — reader role only, one per index. */
  private final Map<String, ScheduledFuture<?>> readerPollFutures = new ConcurrentHashMap<>();

  public IndexManager(
      IndexContainer container, AnalyzerManager analyzerManager, AppRoleConfig roleConfig) {
    this.container = container;
    this.analyzerManager = analyzerManager;
    this.roleConfig = roleConfig;
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Opens a Lucene index at the given path. Delegates to the writer or reader implementation based
   * on the configured role.
   *
   * @param indexName logical name of the index
   * @param indexPath filesystem path to the index directory
   * @throws IllegalStateException if the index cannot be opened
   */
  public void openIndex(IndexName indexName, String indexPath) {
    if (roleConfig.isWriterRole()) {
      openWriterIndex(indexName, indexPath);
    } else {
      openReaderIndex(indexName, indexPath);
    }
  }

  public IndexWriter getPageTabIndexWriter() {
    return container.getIndexWriter(IndexName.PAGE_TAB);
  }

  public IndexWriter getSubmissionIndexWriter() {
    return container.getIndexWriter(IndexName.SUBMISSION);
  }

  public IndexWriter getEFOIndexWriter() {
    return container.getIndexWriter(IndexName.EFO);
  }

  public IndexWriter getFilesIndexWriter() {
    return container.getIndexWriter(IndexName.FILES);
  }

  public IndexSearcher acquireSearcher(IndexName indexName) throws IOException {
    return container.acquireSearcher(indexName);
  }

  public void releaseSearcher(IndexName indexName, IndexSearcher searcher) throws IOException {
    container.releaseSearcher(indexName, searcher);
  }

  /** Forces a blocking refresh of all SearcherManagers. Meaningful on writer role only. */
  public void refreshAll() {
    container
        .getAllSearcherManagers()
        .values()
        .forEach(
            sm -> {
              try {
                sm.maybeRefreshBlocking();
              } catch (IOException e) {
                log.error("Refresh failed", e);
              }
            });
  }

  public void commitSubmissionRelatedIndices() throws IOException {
    container.getIndexWriter(IndexName.SUBMISSION).commit();
    container.getIndexWriter(IndexName.FILES).commit();
    container.getIndexWriter(IndexName.PAGE_TAB).commit();
  }

  @PreDestroy
  public void closeAll() {
    log.info("Closing all Lucene index resources...");

    // Stop writer NRT threads
    reopenThreads.values().forEach(ControlledRealTimeReopenThread::close);

    // Stop reader poll tasks
    readerPollFutures.values().forEach(f -> f.cancel(false));
    readerPollScheduler.shutdown();

    // Close all SearcherManagers first — drains in-flight searchers
    container
        .getAllSearcherManagers()
        .values()
        .forEach(
            sm -> {
              try {
                sm.close();
              } catch (IOException e) {
                log.error("SearcherManager close failed", e);
              }
            });

    // Close all writers — flushes pending docs and releases write.lock
    container
        .getAllIndexWriters()
        .values()
        .forEach(
            w -> {
              try {
                w.close();
              } catch (IOException e) {
                log.error("IndexWriter close failed", e);
              }
            });
  }

  // ---------------------------------------------------------------------------
  // Writer role
  // ---------------------------------------------------------------------------

  /**
   * Opens the full NRT writer stack for a single index.
   *
   * <p>Stack: {@code IndexWriter} (local NVMe, FSDirectory) → {@code SearcherManager} → {@code
   * ControlledRealTimeReopenThread}. Readers see new documents within {@value
   * TARGET_MAX_STALE_SEC}s of {@code addDocument()}, before any {@code commit()} is required.
   *
   * @param indexName logical index name
   * @param indexPath path to the local NVMe index directory
   */
  private void openWriterIndex(IndexName indexName, String indexPath) {
    String indexNameStr = indexName.getIndexName();
    log.info("Opening writer index {} [{}]", indexNameStr, indexPath);
    try {
      FSDirectory directory = FSDirectory.open(Paths.get(indexPath));

      IndexWriterConfig config = createWriterConfig();
      setSnapshotPolicy(config, new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy()));

      IndexWriter writer = new IndexWriter(directory, config);

      container.setIndexWriter(indexName, writer);

      SearcherManager manager =
          new SearcherManager(
              writer,
              new SearcherFactory() {
                @Override
                public IndexSearcher newSearcher(IndexReader r, IndexReader prev) {
                  return new IndexSearcher(r);
                }
              });
      container.setSearcherManager(indexName, manager);

      ControlledRealTimeReopenThread<IndexSearcher> thread =
          new ControlledRealTimeReopenThread<>(
              writer, manager, TARGET_MAX_STALE_SEC, TARGET_MIN_STALE_SEC);
      thread.setDaemon(true);
      thread.setName("nrt-reopen-" + indexNameStr);
      thread.start();
      reopenThreads.put(indexNameStr, thread);

      log.info(
          "Writer index {} [{}] open and ready (NRT staleness {}-{}s)",
          indexNameStr,
          indexPath,
          TARGET_MIN_STALE_SEC,
          TARGET_MAX_STALE_SEC);

    } catch (IOException e) {
      log.error("Error opening writer index {} [{}]", indexNameStr, indexPath, e);
      throw new IllegalStateException("Failed to open writer index: " + indexNameStr, e);
    }
  }

  // ---------------------------------------------------------------------------
  // Reader role
  // ---------------------------------------------------------------------------

  /**
   * Opens a read-only index stack for a single index against the NFS snapshot directory.
   *
   * <p>Stack: {@code NIOFSDirectory} (NFS-safe, no mmap) → {@code SearcherManager}. A background
   * poll task calls {@link SearcherManager#maybeRefresh()} every {@value
   * READER_POLL_INTERVAL_SEC}s, which internally delegates to {@link DirectoryReader#openIfChanged}
   * and swaps the reader atomically when the writer has produced a new commit.
   *
   * <p>No {@code IndexWriter} is opened — this pod never acquires {@code write.lock}.
   *
   * @param indexName logical index name
   * @param indexPath path to the NFS snapshot directory
   */
  private void openReaderIndex(IndexName indexName, String indexPath) {
    String indexNameStr = indexName.getIndexName();
    log.info("Opening reader index {} [{}]", indexNameStr, indexPath);
    try {
      NIOFSDirectory directory = new NIOFSDirectory(Paths.get(indexPath));

      SearcherManager manager =
          new SearcherManager(
              directory,
              new SearcherFactory() {
                @Override
                public IndexSearcher newSearcher(IndexReader r, IndexReader prev) {
                  return new IndexSearcher(r);
                }
              });
      container.setSearcherManager(indexName, manager);

      // Seed the generation so the first poll has a baseline to compare against
      long initialGeneration = getCurrentGeneration(manager);
      lastSeenGeneration.put(indexNameStr, initialGeneration);
      log.info(
          "Reader index {} [{}] open at generation {}", indexNameStr, indexPath, initialGeneration);

      ScheduledFuture<?> pollFuture =
          readerPollScheduler.scheduleWithFixedDelay(
              () -> pollForIndexChanges(indexName, indexNameStr),
              READER_POLL_INTERVAL_SEC,
              READER_POLL_INTERVAL_SEC,
              TimeUnit.SECONDS);
      readerPollFutures.put(indexNameStr, pollFuture);

      log.info(
          "Reader index {} [{}] poll scheduled every {}s",
          indexNameStr,
          indexPath,
          READER_POLL_INTERVAL_SEC);

    } catch (IOException e) {
      log.error("Error opening reader index {} [{}]", indexNameStr, indexPath, e);
      throw new IllegalStateException("Failed to open reader index: " + indexNameStr, e);
    }
  }

  /**
   * Poll task executed on the reader scheduler. Calls {@link SearcherManager#maybeRefresh()}, which
   * internally delegates to {@link DirectoryReader#openIfChanged}.
   *
   * <p>If the index has not changed, this is a near-zero-cost {@code segments_N} read. If changed,
   * a new reader is opened sharing unchanged segments with the previous one. The old reader is
   * released by {@link SearcherManager} once no searchers hold a reference.
   *
   * <p>Exceptions are logged but not rethrown — a failed poll retains the current (stale) reader,
   * which is preferable to crashing the scheduler thread.
   */
  private void pollForIndexChanges(IndexName indexName, String indexNameStr) {
    try {
      SearcherManager manager = container.getSearcherManager(indexName);

      long currentGeneration = getCurrentGeneration(manager);

      if (currentGeneration <= lastSeenGeneration.getOrDefault(indexNameStr, -1L)) {
        log.trace("Index {} unchanged at poll (generation {})", indexNameStr, currentGeneration);
        return; // bail out before calling maybeRefresh() at all
      }

      // Generation has advanced — now worth refreshing
      boolean refreshed = manager.maybeRefresh();
      if (refreshed) {
        long newGeneration = getCurrentGeneration(manager);
        log.info(
            "Index {} refreshed — generation {} → {}",
            indexNameStr,
            lastSeenGeneration.getOrDefault(indexNameStr, -1L),
            newGeneration);
        lastSeenGeneration.put(indexNameStr, newGeneration);
      }

    } catch (IOException e) {
      log.error("Poll failed for {} — retaining current reader", indexNameStr, e);
    }
  }

  private long getCurrentGeneration(SearcherManager manager) throws IOException {
    IndexSearcher searcher = manager.acquire();
    try {
      return ((DirectoryReader) searcher.getIndexReader()).getIndexCommit().getGeneration();
    } finally {
      manager.release(searcher);
    }
  }

  // ---------------------------------------------------------------------------
  // Shared helpers
  // ---------------------------------------------------------------------------

  private IndexWriterConfig createWriterConfig() {
    IndexWriterConfig config = new IndexWriterConfig(analyzerManager.getPerFieldAnalyzerWrapper());
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    return config;
  }

  private void setSnapshotPolicy(IndexWriterConfig config, SnapshotDeletionPolicy snapshotPolicy) {
    if (snapshotPolicy == null) {
      snapshotPolicy = new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
    }
    config.setIndexDeletionPolicy(snapshotPolicy);
  }
}
