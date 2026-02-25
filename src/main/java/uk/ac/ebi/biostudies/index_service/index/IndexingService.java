package uk.ac.ebi.biostudies.index_service.index;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
 * Manages asynchronous indexing of BioStudies submissions using bounded thread pool executor.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>{@code threadCount} daemon threads with {@code queueCapacity} backpressure
 *   <li>Real-time task tracking via {@link ConcurrentHashMap} with 1h auto-expiry
 *   <li>WebSocket health checks before queuing
 *   <li>REST endpoints for status monitoring
 * </ul>
 *
 * <p>Queue position = active threads + queued tasks + 1. Tasks track progress via {@link
 * TaskStatus}.
 */
@Slf4j
@Service
public class IndexingService {

  /** Active indexing tasks with 1h TTL. */
  private final ConcurrentHashMap<String, TaskStatus> tasks = new ConcurrentHashMap<>();

  /** Schedules expired task cleanup. */
  private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);

  private final ObjectProvider<WebSocketConnectionService> webSocketProvider;
  private final SubmissionIndexer submissionIndexer;
  private final IndexingTransactionManager indexingTransactionManager;
  private final PaginatedExtSubmissionHttpClient paginatedClient;

  /** Tracks concurrent indexing operations. */
  private final AtomicInteger activeTasks = new AtomicInteger(0);

  private ThreadPoolExecutor threadPoolExecutor;

  @Value("${indexer.thread-count:8}")
  private int threadCount;

  @Value("${indexer.queue-capacity:100}")
  private int queueCapacity;

  /**
   * Constructs service and initializes dependencies.
   *
   * @param webSocketProvider WebSocket status service provider
   * @param submissionIndexer submission document indexer
   * @param indexingTransactionManager transaction coordinator
   * @param paginatedClient client for single + paginated submission fetching
   */
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

  @PostConstruct
  public void init() {
    ExecutorService executor =
        new ThreadPoolExecutor(
            threadCount,
            threadCount,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            r -> {
              Thread t = new Thread(r, String.format("indexer-%d", threadCount));
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure: caller waits
            );
    log.info("IndexingService initialized: threads={}, queue={}", threadCount, queueCapacity);
    this.threadPoolExecutor = (ThreadPoolExecutor) executor;
  }

  /**
   * Queues submission for async indexing. Returns tracking information immediately.
   *
   * <p>Default: queueSubmission(accNo) → removeFileDocuments=true, commit=true
   */
  public IndexingInfo queueSubmission(String accNo) {
    return queueSubmission(accNo, true, true);
  }

  /**
   * Queues submission for async indexing with configurable options.
   *
   * @param accNo accession number (e.g. "S-BSST123")
   * @param removeFileDocuments if true, removes stale file documents during indexing
   * @param commit if true, commits transaction after indexing (false for batching)
   * @return immediate tracking info with queue position and status URL
   * @throws ServiceUnavailableException if WebSocket unhealthy
   */
  public IndexingInfo queueSubmission(String accNo, boolean removeFileDocuments, boolean commit) {
    if (webSocketProvider.getObject().isClosed()) {
      throw new ServiceUnavailableException("Websocket connection is closed");
    }

    TaskStatus status = new TaskStatus(accNo);
    tasks.put(accNo, status);

    // Correct position: active + queued + this task
    int queuePosition = activeTasks.get() + threadPoolExecutor.getQueue().size() + 1;
    String taskId = status.getTaskId();
    String statusUrl = "/submissions/" + accNo + "/status";

    log.info("[{}]: queued at position {} (taskId={})", accNo, queuePosition, taskId);

    activeTasks.incrementAndGet();
    threadPoolExecutor.submit(
        () -> {
          try {
            indexSubmissionAsync(accNo, status, removeFileDocuments, commit);
          } catch (Exception e) {
            status.setFailed(e.getMessage());
            log.error("Indexing failed [{}]: {}", accNo, e.getMessage(), e);
          } finally {
            activeTasks.decrementAndGet();
            // Cleanup after 1h
            cleanupScheduler.schedule(
                () -> {
                  tasks.remove(accNo);
                  log.debug("Cleaned expired task: {}", accNo);
                },
                1,
                TimeUnit.HOURS);
          }
        });

    return new IndexingInfo(accNo, queuePosition, taskId, statusUrl);
  }

  /**
   * Asynchronously indexes single submission with progress updates.
   *
   * @param accNo submission accession
   * @param status task status tracker
   * @param removeFileDocuments indicates if files related info should be deleted. It is false if
   *     re-indexing all submissions, as indexes are cleaned first
   * @param commit should process commit after finishing or delegate
   */
  private void indexSubmissionAsync(
      String accNo, TaskStatus status, boolean removeFileDocuments, boolean commit) {
    long startTime = System.currentTimeMillis();
    status.setRunning();

    try {
      log.info("[{}]: fetching external metadata", accNo);
      ExtSubmissionFetchResult result = paginatedClient.fetchExtSubmissionByAccNo(accNo);

      if (ExtSubmissionFetchStatus.FOUND.equals(result.status())) {
        log.info("[{}]: indexing submission", accNo);
        ExtendedSubmissionMetadata metadata = result.metadata();
        submissionIndexer.indexOne(metadata, removeFileDocuments, commit);
        status.setCompleted();
        log.info("[{}]: completed in {}ms", accNo, System.currentTimeMillis() - startTime);
      } else {
        String msg = String.format("Submission %s: %s", accNo, result.status());
        log.warn(msg);
        status.setFailed(msg);
      }
    } catch (Exception e) {
      String msg = String.format("Indexing failed: %s", e.getMessage());
      log.error("[{}]: {}", accNo, msg, e);
      status.setFailed(msg);
    }
  }

  /**
   * Queues paginated stream indexing for large collections.
   *
   * @param filters submission filters (collection, release date, etc.)
   * @param pageSize batch size for streaming (default ~100)
   * @return stream tracking info
   */
  public IndexingInfo queueStream(ExtSubmissionFilters filters, int pageSize) {
    String streamTaskId = UUID.randomUUID().toString();
    TaskStatus streamStatus = new TaskStatus("stream-" + streamTaskId);
    tasks.put(streamTaskId, streamStatus);

    activeTasks.incrementAndGet();
    threadPoolExecutor.submit(
        () -> { // Main thread pool
          try {
            AtomicLong totalProcessed = new AtomicLong(0);

            paginatedClient.processAllExtSubmissionsStream(
                filters,
                page -> {
                  // Page batching with indexWithoutCommit
                  for (ExtendedSubmissionMetadata metadata : page.getContent()) {
                    submissionIndexer.indexWithoutCommit(metadata, true);

                    totalProcessed.incrementAndGet();
                  }
                  indexingTransactionManager.commit(); // Every ~100
                  log.info("Stream [{}]: page complete, total: {}", streamTaskId, totalProcessed);
                },
                pageSize);
            streamStatus.setCompleted();
          } catch (Exception e) {
            streamStatus.setFailed(e.getMessage());
          } finally {
            activeTasks.decrementAndGet();
          }
        });

    int queuePosition = activeTasks.get() + threadPoolExecutor.getQueue().size() + 1;
    return new IndexingInfo(
        streamTaskId,
        queuePosition,
        streamStatus.getTaskId(),
        "/streams/" + streamTaskId + "/status");
  }

  /**
   * Returns task status or NOT_FOUND stub if expired.
   *
   * @param accNo submission accession or stream ID
   * @return current {@link TaskStatus} or NOT_FOUND stub
   */
  public TaskStatus getStatus(String accNo) {
    return tasks.getOrDefault(accNo, TaskStatus.createNotFound(accNo));
  }

  /**
   * Returns list of active (QUEUED/RUNNING) tasks only.
   *
   * @return active tasks, sorted newest first
   */
  public List<TaskStatus> getAllActiveTasks() {
    return tasks.values().stream()
        .filter(t -> t.getState() == TaskState.QUEUED || t.getState() == TaskState.RUNNING)
        .sorted((a, b) -> Long.compare(b.getQueuedAt(), a.getQueuedAt()))
        .toList();
  }

  /**
   * Returns current queue size (pending tasks).
   *
   * @return number of queued tasks
   */
  public int getQueueSize() {
    return threadPoolExecutor.getQueue().size();
  }

  /**
   * Returns count of active (running) indexing threads.
   *
   * @return active thread count
   */
  public int getActiveTasks() {
    return activeTasks.get();
  }

  /**
   * Gracefully awaits all indexing completion (max 5 hours) then commits transactions.
   *
   * @throws InterruptedException if interrupted
   * @throws IOException if final commit fails
   */
  public void awaitCompletion() throws InterruptedException, IOException {
    log.info("Shutting down: active={}, queued={}", getActiveTasks(), getQueueSize());
    threadPoolExecutor.shutdown();
    if (!threadPoolExecutor.awaitTermination(5, TimeUnit.HOURS)) {
      log.warn("Force shutdown after timeout");
      threadPoolExecutor.shutdownNow();
    }
    indexingTransactionManager.commit();
    log.info("Indexing shutdown complete");
  }

  /**
   * Synchronously deletes submission from index.
   *
   * @param accNo submission accession (e.g. "S-BSST123")
   * @return deletion result with success status and timing
   * @throws ServiceUnavailableException if WebSocket unhealthy
   */
  public void deleteSubmission(String accNo) {
    if (webSocketProvider.getObject().isClosed()) {
      throw new ServiceUnavailableException("Websocket connection is closed");
    }

    long startTime = System.currentTimeMillis();

    try {
      log.info("[{}]: deleting submission", accNo);
      submissionIndexer.deleteSubmission(accNo);
      long duration = System.currentTimeMillis() - startTime;
      log.info("[{}]: deleted in {}ms", accNo, duration);

    } catch (Exception e) {
      String msg = String.format("Deletion failed: %s", e.getMessage());
      log.error("[{}]: {}", accNo, msg, e);
    }
  }

  @PreDestroy
  public void shutdown() {
    log.info("PreDestroy: shutting down pools");
    threadPoolExecutor.shutdown();
    cleanupScheduler.shutdown();
  }
}
