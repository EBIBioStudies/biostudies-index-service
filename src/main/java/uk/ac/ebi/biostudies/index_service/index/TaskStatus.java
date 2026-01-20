package uk.ac.ebi.biostudies.index_service.index;

import lombok.Getter;

@Getter
public class TaskStatus {
  private final String accNo;
  private final String taskId; // Now nullable!
  private final long queuedAt;
  private volatile TaskState state;
  private volatile String message = "";

  /** Normal task constructor (with UUID). */
  public TaskStatus(String accNo) {
    this.accNo = accNo;
    this.taskId = java.util.UUID.randomUUID().toString();
    this.queuedAt = System.currentTimeMillis();
    this.state = TaskState.QUEUED;
  }

  // Private constructor for ghost (null taskId)
  private TaskStatus(String accNo, long queuedAt, TaskState state, String message) {
    this.accNo = accNo;
    this.taskId = null; // Ghost has no taskId
    this.queuedAt = queuedAt;
    this.state = state;
    this.message = message;
  }

  /** Creates "ghost" NOT_FOUND status (null taskId). */
  public static TaskStatus createNotFound(String accNo) {
    return new TaskStatus(
        accNo, 0, TaskState.NOT_FOUND, String.format("No active indexing task for %s", accNo));
  }

  // Update methods (thread-safe via volatile)
  public void setRunning() {
    this.state = TaskState.RUNNING;
  }

  public void setCompleted() {
    this.state = TaskState.COMPLETED;
  }

  public void setFailed(String error) {
    this.state = TaskState.FAILED;
    this.message = error;
  }
}
