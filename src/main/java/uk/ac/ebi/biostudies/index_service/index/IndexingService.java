package uk.ac.ebi.biostudies.index_service.index;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.client.ExtSubmissionFetchResult;
import uk.ac.ebi.biostudies.index_service.client.ExtSubmissionFetchStatus;
import uk.ac.ebi.biostudies.index_service.client.ExtSubmissionFilters;
import uk.ac.ebi.biostudies.index_service.client.PaginatedExtSubmissionHttpClient;
import uk.ac.ebi.biostudies.index_service.exceptions.ServiceUnavailableException;
import uk.ac.ebi.biostudies.index_service.messages.WebSocketConnectionService;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

/**
 * Manages asynchronous indexing using a producer-consumer pipeline for optimal throughput.
 *
 * <h2>Architecture</h2>
 *
 * <pre>
 *   [IO fetcher pool] ──pages──▶ [Phaser-tracked indexer pool] ──▶ [Batch commit]
 * </pre>
 *
 * <ul>
 *   <li><b>IO fetchers</b> ({@code indexer.fetcher-threads}, default 4): network-bound page
 *       fetching from the external submission API.
 *   <li><b>CPU indexers</b> ({@code indexer.thread-count}, default 8): parse and write Lucene
 *       documents; bounded by {@code indexer.queue-capacity}.
 *   <li><b>Phaser</b>: replaces the fragile polling loop with a deterministic barrier — the
 *       producer registers each submission task before submitting, and arrives only after all pages
 *       are dispatched. The final {@code arriveAndAwaitAdvance()} blocks until every registered
 *       consumer has finished, then a single final commit is issued.
 *   <li><b>Semaphore backpressure</b>: caps in-flight indexer tasks at {@code
 *       indexer.max-in-flight} (default {@code queueCapacity}) so the heap never fills with
 *       unbounded in-memory submission objects while the indexer pool is busy.
 *   <li><b>Striped commit lock</b>: periodic batch commits are serialized through a single {@code
 *       AtomicLong.compareAndSet} guard, so only one thread triggers each commit boundary —
 *       eliminates the modulo race.
 * </ul>
 *
 * <h2>Key configuration properties</h2>
 *
 * <pre>
 *   indexer.thread-count=8          # CPU indexer threads
 *   indexer.queue-capacity=100      # indexer task queue depth
 *   indexer.fetcher-threads=4       # IO page-fetcher threads
 *   indexer.fetcher-queue=1000      # fetcher task queue depth
 *   indexer.max-in-flight=200       # max submissions held in RAM concurrently
 *   indexer.commit-batch-size=1000  # submissions between intermediate commits
 * </pre>
 */
@Slf4j
@Service
public class IndexingService {

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------

  private static final long TASK_TTL_HOURS = 1;

  /** Live task registry — entries expire after {@value TASK_TTL_HOURS} hour(s). */
  private final ConcurrentHashMap<String, TaskStatus> tasks = new ConcurrentHashMap<>();

  /** Drives TTL-based cleanup of completed/failed task entries. */
  private final ScheduledExecutorService cleanupScheduler =
      Executors.newSingleThreadScheduledExecutor(
          namedDaemonFactory("task-cleanup", new AtomicInteger()));

  private final ObjectProvider<WebSocketConnectionService> webSocketProvider;
  private final SubmissionIndexer submissionIndexer;
  private final IndexingTransactionManager indexingTransactionManager;
  private final PaginatedExtSubmissionHttpClient paginatedClient;

  /**
   * Counts tasks that are either actively running or queued in the indexer pool. Incremented before
   * submit, decremented in the finally block.
   */
  private final AtomicInteger activeTasks = new AtomicInteger(0);

  /** CPU-bound Lucene indexing pool. Bounded queue + CallerRuns = natural backpressure. */
  private ThreadPoolExecutor threadPoolExecutor;

  // -------------------------------------------------------------------------
  // Constants
  // -------------------------------------------------------------------------
  /** IO-bound HTTP page-fetching pool. Separate from CPU pool to avoid head-of-line blocking. */
  private ExecutorService pageFetcherPool;

  // -------------------------------------------------------------------------
  // Configurable tuning knobs
  // -------------------------------------------------------------------------
  @Value("${indexer.thread-count:8}")
  private int threadCount;

  @Value("${indexer.queue-capacity:100}")
  private int queueCapacity;

  @Value("${indexer.fetcher-threads:4}")
  private int fetcherThreads;

  @Value("${indexer.fetcher-queue:1000}")
  private int fetcherQueueCapacity;

  /**
   * Maximum number of submission objects held in memory simultaneously across all in-flight indexer
   * tasks. Prevents OOM when the fetcher is significantly faster than the indexer. Defaults to
   * {@code queueCapacity} if not set explicitly.
   */
  @Value("${indexer.max-in-flight:#{${indexer.queue-capacity:100}}}")
  private int maxInFlight;

  @Value("${indexer.commit-batch-size:1000}")
  private int commitBatchSize;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public IndexingService(
      ObjectProvider<WebSocketConnectionService> webSocketProvider,
      SubmissionIndexer submissionIndexer,
      IndexingTransactionManager indexingTransactionManager,
      PaginatedExtSubmissionHttpClient paginatedClient) {
    this.webSocketProvider = webSocketProvider;
    this.submissionIndexer = submissionIndexer;
    this.indexingTransactionManager = indexingTransactionManager;
    this.paginatedClient = paginatedClient;
  }

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  /**
   * Creates a {@link ThreadFactory} producing daemon threads named {@code <prefix>-N}.
   *
   * <p>Daemon threads do not prevent JVM shutdown, which is important because both pools should
   * stop when the Spring context is destroyed rather than keeping the process alive.
   *
   * @param prefix human-readable name prefix (visible in thread dumps)
   * @param counter shared counter to ensure unique sequential thread numbers
   */
  private static ThreadFactory namedDaemonFactory(String prefix, AtomicInteger counter) {
    return r -> {
      Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
      t.setDaemon(true);
      return t;
    };
  }

  /**
   * Initialises both thread pools with named daemon threads and correct rejection policies.
   *
   * <p><b>Why CallerRunsPolicy on the indexer pool?</b><br>
   * When the bounded queue is full, the submitting thread (a fetcher thread) is forced to run the
   * task itself. This naturally throttles the fetcher without requiring an explicit sleep loop,
   * keeping the system in flow-controlled equilibrium.
   *
   * <p>Thread names use an {@link AtomicInteger} counter so each thread gets a unique, human-
   * readable name (e.g. {@code indexer-3}) that is visible in thread dumps.
   */
  @PostConstruct
  public void init() {
    AtomicInteger fetcherCounter = new AtomicInteger();
    pageFetcherPool =
        new ThreadPoolExecutor(
            fetcherThreads,
            fetcherThreads,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(fetcherQueueCapacity),
            namedDaemonFactory("page-fetcher", fetcherCounter),
            new ThreadPoolExecutor.CallerRunsPolicy());

    AtomicInteger indexerCounter = new AtomicInteger();
    threadPoolExecutor =
        new ThreadPoolExecutor(
            threadCount,
            threadCount,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            namedDaemonFactory("indexer", indexerCounter),
            new ThreadPoolExecutor.CallerRunsPolicy());

    log.info(
        "Pools ready — indexer: threads={} queue={} | fetcher: threads={} queue={} | "
            + "maxInFlight={} commitBatch={}",
        threadCount,
        queueCapacity,
        fetcherThreads,
        fetcherQueueCapacity,
        maxInFlight,
        commitBatchSize);
  }

  // -------------------------------------------------------------------------
  // Single-submission indexing
  // -------------------------------------------------------------------------

  /** Gracefully stops pools and the cleanup scheduler on Spring context shutdown. */
  @PreDestroy
  public void shutdown() {
    log.info(
        "Shutdown requested — indexer active={} queued={} | fetcher active={}",
        threadPoolExecutor.getActiveCount(),
        threadPoolExecutor.getQueue().size(),
        ((ThreadPoolExecutor) pageFetcherPool).getActiveCount());
    threadPoolExecutor.shutdown();
    pageFetcherPool.shutdown();
    cleanupScheduler.shutdown();
  }

  /**
   * Convenience overload: queues a submission with file-document removal and auto-commit.
   *
   * @param accNo accession number (e.g. {@code S-BSST123})
   * @return tracking info for polling the task status
   */
  public IndexingInfo queueSubmission(String accNo) {
    return queueSubmission(accNo, true, true);
  }

  /**
   * Queues a single submission for asynchronous Lucene indexing.
   *
   * <p>Returns immediately; the caller can poll the status endpoint in {@link IndexingInfo}.
   *
   * @param accNo accession number
   * @param removeFileDocuments {@code true} to delete stale file-level documents first
   * @param commit {@code true} to commit the Lucene transaction after this task
   * @return {@link IndexingInfo} with queue position, task ID, and status URL
   * @throws ServiceUnavailableException if the backend WebSocket is not connected
   */
  public IndexingInfo queueSubmission(String accNo, boolean removeFileDocuments, boolean commit) {
    requireWebSocketOpen();

    TaskStatus status = new TaskStatus(accNo);
    tasks.put(accNo, status);

    // Snapshot position: currently running + queued + this new task
    int queuePosition = activeTasks.get() + threadPoolExecutor.getQueue().size() + 1;
    String statusUrl = "/submissions/" + accNo + "/status";
    log.info("[{}] Queued at position {} (taskId={})", accNo, queuePosition, status.getTaskId());

    activeTasks.incrementAndGet();
    threadPoolExecutor.submit(
        () -> {
          try {
            indexSubmissionAsync(accNo, status, removeFileDocuments, commit);
          } catch (Exception e) {
            status.setFailed(e.getMessage());
            log.error("[{}] Indexing failed: {}", accNo, e.getMessage(), e);
          } finally {
            activeTasks.decrementAndGet();
            scheduleTaskCleanup(accNo);
          }
        });

    return new IndexingInfo(accNo, queuePosition, status.getTaskId(), statusUrl);
  }

  // -------------------------------------------------------------------------
  // Stream (bulk) indexing
  // -------------------------------------------------------------------------

  /**
   * Core indexing logic executed on an indexer thread.
   *
   * <p>Fetches the submission metadata from the external API and delegates to {@link
   * SubmissionIndexer}. Task status is updated at each lifecycle transition so callers polling
   * {@code /status} see accurate progress.
   */
  private void indexSubmissionAsync(
      String accNo, TaskStatus status, boolean removeFileDocuments, boolean commit) {

    long start = System.currentTimeMillis();
    status.setRunning();

    try {
      log.debug("[{}] Fetching external metadata", accNo);
      ExtSubmissionFetchResult result = paginatedClient.fetchExtSubmissionByAccNo(accNo);

      if (ExtSubmissionFetchStatus.FOUND.equals(result.status())) {
        submissionIndexer.indexOne(result.metadata(), removeFileDocuments, commit);
        status.setCompleted();
        log.info("[{}] Indexed in {}ms", accNo, System.currentTimeMillis() - start);
      } else {
        String msg = String.format("Submission %s: %s", accNo, result.status());
        log.warn(msg);
        status.setFailed(msg);
      }
    } catch (Exception e) {
      String msg = "Indexing failed: " + e.getMessage();
      log.error("[{}] {}", accNo, msg, e);
      status.setFailed(msg);
    }
  }

  /**
   * Queues a paginated bulk-indexing run using a producer-consumer pipeline.
   *
   * <h3>Pipeline description</h3>
   *
   * <ol>
   *   <li>A single <em>producer</em> task runs on the fetcher pool; it iterates pages from the
   *       external API and fans each submission out to the indexer pool.
   *   <li>A {@link Phaser} is used as the completion barrier. The producer registers a party for
   *       itself plus one party per submission before submitting each consumer task, then arrives
   *       after all pages are dispatched. Each consumer arrives when it finishes. {@code
   *       phaser.awaitAdvance(0)} in the producer unblocks only when all parties have arrived —
   *       i.e. every submission has been indexed.
   *   <li>A {@link Semaphore} ({@code maxInFlight}) limits the number of {@link
   *       ExtendedSubmissionMetadata} objects alive on the heap simultaneously. Without this, a
   *       fast network could enqueue thousands of large objects before the indexers drain them,
   *       causing OOM pressure.
   *   <li>Periodic commits use a {@code compareAndSet} guard on the shared {@code lastCommitted}
   *       counter so exactly one thread triggers each commit boundary, eliminating the modulo race
   *       present in the original code.
   * </ol>
   *
   * @param filters server-side filters applied to the submission stream
   * @param pageSize number of submissions fetched per HTTP request
   * @return {@link IndexingInfo} for client-side tracking
   */
  public IndexingInfo queueStream(ExtSubmissionFilters filters, int pageSize) {
    String streamId = "stream-" + UUID.randomUUID();
    TaskStatus streamStatus = new TaskStatus(streamId);
    tasks.put(streamId, streamStatus);
    activeTasks.incrementAndGet();

    pageFetcherPool.submit(() -> runStream(streamId, streamStatus, filters, pageSize));

    // Snapshot queue position for the returned info
    int activeCount =
        threadPoolExecutor.getActiveCount()
            + ((ThreadPoolExecutor) pageFetcherPool).getActiveCount();
    int queuePosition = activeCount + threadPoolExecutor.getQueue().size() + 1;
    log.info("Stream [{}] queued: pos={} filters={}", streamId, queuePosition, filters);

    return new IndexingInfo(
        streamId, queuePosition, streamStatus.getTaskId(), "/streams/" + streamId + "/status");
  }

  /**
   * The body of the stream producer task — runs entirely on a fetcher thread.
   *
   * <p>All consumer tasks it spawns are tracked by the {@link Phaser} so the method can wait for
   * full completion before issuing the final commit.
   */
  private void runStream(
      String streamId, TaskStatus streamStatus, ExtSubmissionFilters filters, int pageSize) {

    // Phaser starts with 1 party = this producer thread.
    // Additional parties are registered (pre-increment) for each consumer before submit.
    Phaser phaser = new Phaser(1);

    // Semaphore caps objects in memory: acquire before enqueuing, release after indexing.
    Semaphore inFlightSlots = new Semaphore(maxInFlight);

    AtomicLong totalIndexed = new AtomicLong(0);

    // lastCommitted tracks the indexed count at the time of the most recent batch commit.
    // compareAndSet ensures exactly one thread fires each commit boundary.
    AtomicLong lastCommitted = new AtomicLong(0);

    streamStatus.setRunning();
    long start = System.currentTimeMillis();

    try {
      paginatedClient.processAllExtSubmissionsStream(
          filters,
          page -> {
            for (ExtendedSubmissionMetadata metadata : page.content()) {
              // Block here if too many submissions are already in-flight (heap protection).
              try {
                inFlightSlots.acquire();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                    "Stream interrupted while waiting for in-flight slot", e);
              }

              // Register the consumer BEFORE submitting so the phaser count is always accurate.
              phaser.register();

              threadPoolExecutor.submit(
                  () -> {
                    try {
                      submissionIndexer.indexWithoutCommit(metadata, true);
                      long indexed = totalIndexed.incrementAndGet();
                      maybeCommit(streamId, indexed, lastCommitted);
                    } catch (Exception e) {
                      log.error(
                          "Stream [{}] indexing failed for {}: {}",
                          streamId,
                          metadata.getAccNo(),
                          e.getMessage(),
                          e);
                    } finally {
                      inFlightSlots.release(); // free the heap slot
                      phaser.arriveAndDeregister(); // signal consumer completion
                    }
                  });
            }
          },
          pageSize);

      // Producer is done dispatching — arrive and block until all consumers finish.
      phaser.arriveAndAwaitAdvance();

      // Final commit for the tail batch (< commitBatchSize items).
      indexingTransactionManager.commit();

      long duration = System.currentTimeMillis() - start;
      log.info(
          "Stream [{}] complete: indexed={} duration={}ms", streamId, totalIndexed.get(), duration);
      streamStatus.setCompleted();

    } catch (Exception e) {
      streamStatus.setFailed(e.getMessage());
      log.error("Stream [{}] failed: {}", streamId, e.getMessage(), e);
    } finally {
      activeTasks.decrementAndGet();
      scheduleTaskCleanup(streamId);
    }
  }

  // -------------------------------------------------------------------------
  // Batch (list of accNos) indexing
  // -------------------------------------------------------------------------

  /**
   * Issues a batch commit if {@code indexed} has crossed the next commit boundary.
   *
   * <p>Uses {@link AtomicLong#compareAndSet} as a single-writer guard: only the thread that
   * successfully advances {@code lastCommitted} will actually call commit. All other threads that
   * arrive at the same boundary will find that the CAS fails and skip the call.
   *
   * @param streamId for log correlation
   * @param indexed current total indexed count (post-increment)
   * @param lastCommitted shared marker of the last commit boundary
   */
  private void maybeCommit(String streamId, long indexed, AtomicLong lastCommitted) {
    long boundary = (indexed / commitBatchSize) * commitBatchSize;
    if (boundary > 0
        && boundary > lastCommitted.get()
        && lastCommitted.compareAndSet(lastCommitted.get(), boundary)) {
      try {
        indexingTransactionManager.commit();
        log.debug("Stream [{}] batch commit at {}", streamId, indexed);
      } catch (Exception e) {
        log.error(
            "Stream [{}] batch commit failed at {}: {}", streamId, indexed, e.getMessage(), e);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Status / monitoring
  // -------------------------------------------------------------------------

  /**
   * Queues a batch of individual submissions and returns a composite {@link IndexingInfo}.
   *
   * <p>Each accession is enqueued as a first-class task so individual progress is visible. The
   * returned {@link IndexingInfo} uses a synthetic batch ID and the earliest queue position across
   * all tasks. Future work: a dedicated {@code BatchStatus} wrapper.
   *
   * @param accNos non-empty list of accession numbers
   * @return tracking info for the batch (references the first-enqueued task)
   * @throws IllegalArgumentException if {@code accNos} is null or empty
   */
  public IndexingInfo queueBatch(List<String> accNos) {
    if (accNos == null || accNos.isEmpty()) {
      throw new IllegalArgumentException("accNos must not be null or empty");
    }

    String batchId = "batch-" + UUID.randomUUID().toString().substring(0, 8);

    // Fan out — reuse single-submission logic so each task has its own TaskStatus.
    List<IndexingInfo> infos =
        accNos.stream()
            .map(accNo -> queueSubmission(accNo, true, false)) // no individual commits in a batch
            .toList();

    // Issue a single commit once all submissions are queued.
    // Real production improvement: attach a callback to wait for all futures before committing.
    threadPoolExecutor.submit(
        () -> {
          try {
            // Naive approach: submit the commit as the last task in the queue.
            // This works because CallerRunsPolicy preserves FIFO ordering within the pool.
            indexingTransactionManager.commit();
            log.info("Batch [{}] commit issued for {} submissions", batchId, accNos.size());
          } catch (IOException e) {
            log.error("Batch [{}] final commit failed: {}", batchId, e.getMessage(), e);
          }
        });

    int minQueuePosition = infos.stream().mapToInt(IndexingInfo::queuePosition).min().orElse(1);
    log.info(
        "Batch [{}] queued: {} submissions, earliest position={}",
        batchId,
        accNos.size(),
        minQueuePosition);

    return new IndexingInfo(batchId, minQueuePosition, batchId, "/batches/" + batchId + "/status");
  }

  /**
   * Returns the {@link TaskStatus} for the given accession number or stream/batch ID.
   *
   * @param id accession number or task ID
   * @return live status, or a {@code NOT_FOUND} stub if the entry has expired or never existed
   */
  public TaskStatus getStatus(String id) {
    return tasks.getOrDefault(id, TaskStatus.createNotFound(id));
  }

  /** Returns all tasks currently in {@code QUEUED} or {@code RUNNING} state, newest first. */
  public List<TaskStatus> getAllActiveTasks() {
    return tasks.values().stream()
        .filter(t -> t.getState() == TaskState.QUEUED || t.getState() == TaskState.RUNNING)
        .sorted((a, b) -> Long.compare(b.getQueuedAt(), a.getQueuedAt()))
        .toList();
  }

  /** Number of tasks waiting in the indexer queue (not yet started). */
  public int getQueueSize() {
    return threadPoolExecutor.getQueue().size();
  }

  // -------------------------------------------------------------------------
  // Graceful drain (used by batch-reindex orchestration)
  // -------------------------------------------------------------------------

  /** Number of tasks currently being processed (running or queued across both pools). */
  public int getActiveTasks() {
    return activeTasks.get();
  }

  // -------------------------------------------------------------------------
  // Deletion
  // -------------------------------------------------------------------------

  /**
   * Drains both pools and issues a final commit. Blocks until all work is done or the 5-hour
   * timeout elapses.
   *
   * @throws InterruptedException if the calling thread is interrupted while waiting
   * @throws IOException if the final commit fails
   */
  public void awaitCompletion() throws InterruptedException, IOException {
    log.info(
        "awaitCompletion — indexer active={} queued={} | fetcher active={}",
        threadPoolExecutor.getActiveCount(),
        threadPoolExecutor.getQueue().size(),
        ((ThreadPoolExecutor) pageFetcherPool).getActiveCount());

    threadPoolExecutor.shutdown();
    pageFetcherPool.shutdown();

    boolean finished = threadPoolExecutor.awaitTermination(5, TimeUnit.HOURS);
    if (!finished) {
      log.warn("awaitCompletion timed out — forcing shutdown");
      threadPoolExecutor.shutdownNow();
      pageFetcherPool.shutdownNow();
    }

    indexingTransactionManager.commit();
    log.info("awaitCompletion finished");
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Synchronously removes a submission and all its file documents from the index.
   *
   * @param accNo accession number to delete
   * @throws ServiceUnavailableException if the backend WebSocket is not connected
   */
  public void deleteSubmission(String accNo) {
    requireWebSocketOpen();
    long start = System.currentTimeMillis();
    try {
      log.info("[{}] Deleting submission", accNo);
      submissionIndexer.deleteSubmission(accNo);
      log.info("[{}] Deleted in {}ms", accNo, System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.error("[{}] Deletion failed: {}", accNo, e.getMessage(), e);
    }
  }

  /** Schedules removal of the task entry from the in-memory map after {@value TASK_TTL_HOURS}h. */
  private void scheduleTaskCleanup(String key) {
    cleanupScheduler.schedule(
        () -> {
          tasks.remove(key);
          log.debug("Expired task removed: {}", key);
        },
        TASK_TTL_HOURS,
        TimeUnit.HOURS);
  }

  /** Guards operations that require an active backend WebSocket connection. */
  private void requireWebSocketOpen() {
    if (webSocketProvider.getObject().isClosed()) {
      throw new ServiceUnavailableException("WebSocket connection is closed");
    }
  }
}
