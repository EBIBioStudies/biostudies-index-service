package uk.ac.ebi.biostudies.index_service.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.biostudies.index_service.index.IndexingInfo;
import uk.ac.ebi.biostudies.index_service.index.IndexingService;
import uk.ac.ebi.biostudies.index_service.index.StreamIndexRequest;
import uk.ac.ebi.biostudies.index_service.index.TaskStatus;

/**
 * REST controller for submission indexing operations.
 *
 * <p>Provides endpoints to queue submissions for asynchronous indexing and monitor task progress.
 * Returns consistent {@link RestResponse} envelopes for all responses.
 *
 * <p><b>Security:</b> Currently unsecured. Add Spring Security authentication/authorization before
 * production (ADMIN role recommended for {@link #listAllTasks()}).
 */
@Slf4j
@RestController
@RequestMapping("/submissions")
@Tag(
    name = "Indexing Operations",
    description =
        """
        Queue submissions for asynchronous indexing and monitor task progress.
        Returns RestResponse<T> envelopes with consistent success/error structure.
        """)
public class SubmissionIndexController {

  private final IndexingService indexingService;

  /**
   * Constructs a new controller.
   *
   * @param indexingService service managing indexing queue and task tracking
   */
  public SubmissionIndexController(IndexingService indexingService) {
    this.indexingService = indexingService;
  }

  /**
   * Queues a submission for asynchronous indexing.
   *
   * <p>Initiates background indexing via thread pool executor. Returns immediately with queue
   * position and task tracking information. Poll progress using {@link #getStatus(String)}.
   *
   * @param accNo submission accession number
   * @return {@link ResponseEntity} with 202 Accepted and {@link IndexingInfo}
   */
  @Operation(
      summary = "Queue submission for indexing",
      description =
          """
          Adds submission to async indexing queue. Returns queue position and task ID for tracking.
          Actual indexing occurs in background thread.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Submission queued successfully",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RestResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid accession format",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RestResponse.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Indexing service unavailable (WebSocket closed)",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RestResponse.class)))
  })
  @PostMapping("/{accNo}/index")
  public ResponseEntity<RestResponse<IndexingInfo>> queueIndexSubmission(
      @Parameter(description = "Submission accession (e.g., S-BSST1432)", example = "S-BSST1432")
          @PathVariable
          String accNo) {
    log.info("Queue indexing requested for submission: {}", accNo);
    IndexingInfo info = indexingService.queueSubmission(accNo);
    String message =
        String.format("Submission %s queued (position: %d)", accNo, info.queuePosition());
    return ResponseEntity.accepted().body(new RestResponse<>(true, message, info, List.of()));
  }

  /**
   * Retrieves real-time indexing status for a submission.
   *
   * <p>Returns task state, progress, and current operation details. Completed tasks auto-expire
   * from memory after 1 hour.
   *
   * @param accNo submission accession number
   * @return {@link ResponseEntity} with 200 OK and {@link TaskStatus}, or 404 if no active task
   */
  @GetMapping("/{accNo}/status")
  @Operation(
      summary = "Get indexing task status",
      description =
          """
          Returns current state (QUEUED/RUNNING/COMPLETED/FAILED) and progress for a submission's
          indexing task. Use polling pattern for long-running tasks.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Task status returned",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RestResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "No active indexing task found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RestResponse.class)))
  })
  public ResponseEntity<RestResponse<TaskStatus>> getStatus(
      @Parameter(description = "Submission accession", example = "S-BSST1432") @PathVariable
          String accNo) {
    log.debug("Status requested for submission: {}", accNo);
    TaskStatus status = indexingService.getStatus(accNo);

    // Always 200 - let client check state field
    String message =
        switch (status.getState()) {
          case NOT_FOUND -> "No indexing task found for " + accNo;
          case COMPLETED -> "Indexing completed successfully";
          case FAILED -> "Indexing failed: " + status.getMessage();
          default -> "Indexing status retrieved";
        };

    return ResponseEntity.ok(RestResponse.success(message, status));
  }

  /**
   * Lists all active indexing tasks system-wide.
   *
   * <p>Returns only queued and running tasks (completed auto-removed). Sorted newest first.
   * Intended for monitoring queue status and diagnostics.
   *
   * @return {@link ResponseEntity} with 200 OK and list of active {@link TaskStatus} objects
   */
  @GetMapping("/tasks")
  @Operation(
      summary = "List all active indexing tasks",
      description =
          """
          Returns system-wide view of queued and running indexing operations.
          Completed tasks excluded (auto-cleaned). Sorted by queued time (newest first).""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "List of active tasks returned",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RestResponse.class)))
  })
  public ResponseEntity<RestResponse<List<TaskStatus>>> listAllTasks() {
    log.debug("Listing all active indexing tasks");
    List<TaskStatus> activeTasks = new ArrayList<>(indexingService.getAllActiveTasks());

    // Sort newest first (descending queuedAt)
    activeTasks.sort((a, b) -> Long.compare(b.getQueuedAt(), a.getQueuedAt()));

    String message = String.format("Found %d active indexing tasks", activeTasks.size());
    return ResponseEntity.ok(RestResponse.success(message, activeTasks));
  }

  /**
   * Queues batch/stream indexing for submissions matching filters.
   *
   * <p>Initiates memory-efficient streaming indexing via pagination. Processes pages asynchronously
   * using page-level commits for scalability. Returns stream coordinator task for monitoring
   * overall progress.
   *
   * @param request batch indexing request with filters and page size
   * @return {@link ResponseEntity} with 202 Accepted and {@link IndexingInfo} for stream task
   */
  @Operation(
      summary = "Queue batch/stream indexing",
      description =
          """
          Streams submissions matching filters (collection, release date, etc.) via pagination.
          Indexes pages asynchronously with configurable pageSize (default: 100).
          Returns stream task ID - monitor individual page progress via logs.""")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "Stream queued successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid filters or pageSize"),
    @ApiResponse(responseCode = "503", description = "Indexing service unavailable")
  })
  @PostMapping("/index/stream") //
  public ResponseEntity<RestResponse<IndexingInfo>> queueStreamIndexing(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Batch indexing request",
              required = true,
              content = @Content(schema = @Schema(implementation = StreamIndexRequest.class)))
          @RequestBody
          StreamIndexRequest request) {

    log.info(
        "Queue stream indexing: filters={}, pageSize={}",
        request.getFilters(),
        request.getPageSize());

    IndexingInfo info = indexingService.queueStream(request.getFilters(), request.getPageSize());

    String message =
        String.format("Stream %s queued (position: %d)", info.taskId(), info.queuePosition());
    return ResponseEntity.accepted().body(new RestResponse<>(true, message, info, List.of()));
  }
}
