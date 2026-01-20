package uk.ac.ebi.biostudies.index_service.client;

public enum ExtSubmissionFetchStatus {
  FOUND,          // 200 OK, metadata and json are present
  NOT_FOUND,      // 404, caller may delete
  ERROR           // non-200/404, caller typically logs/ignores or retries
}
