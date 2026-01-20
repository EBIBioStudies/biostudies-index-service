package uk.ac.ebi.biostudies.index_service.index;

public enum TaskState {
  QUEUED,
  RUNNING,
  COMPLETED,
  FAILED,
  CANCELLED,
  NOT_FOUND
}
