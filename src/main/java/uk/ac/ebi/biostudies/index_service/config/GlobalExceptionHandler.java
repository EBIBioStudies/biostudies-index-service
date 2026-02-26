package uk.ac.ebi.biostudies.index_service.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uk.ac.ebi.biostudies.index_service.exceptions.ServiceUnavailableException;
import uk.ac.ebi.biostudies.index_service.exceptions.SubmissionNotAccessibleException;
import uk.ac.ebi.biostudies.index_service.rest.ApiError;
import uk.ac.ebi.biostudies.index_service.rest.RestResponse;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<RestResponse<Void>> handleServiceUnavailable(
      ServiceUnavailableException ex) {

    ApiError error =
        new ApiError("WEBSOCKET_CLOSED", null, ex.getMessage(), HttpStatus.SC_SERVICE_UNAVAILABLE);
    return ResponseEntity.status(HttpStatus.SC_SERVICE_UNAVAILABLE)
        .body(RestResponse.error("Indexing unavailable", List.of(error)));
  }

  /**
   * Maps permission errors to 403 Forbidden.
   */
  @ExceptionHandler(SubmissionNotAccessibleException.class)
  public ResponseEntity<RestResponse<Void>> handleNotAccessible(SubmissionNotAccessibleException ex) {
    ApiError error =
        new ApiError("SUBMISSION_NOT_ACCESSIBLE", null, ex.getMessage(), HttpStatus.SC_FORBIDDEN);
    return ResponseEntity.status(HttpStatus.SC_FORBIDDEN)
        .body(RestResponse.error("Submission not accessible", List.of(error)));
  }

  /**
   * Silently ignores favicon.ico and other missing static resource requests.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
    return ResponseEntity.notFound().build();
  }

  // Catch-all for other exceptions
  @ExceptionHandler(Exception.class)
  public ResponseEntity<RestResponse<Void>> handleGeneric(Exception ex) {
    log.error("Unexpected error", ex);
    ApiError error = new ApiError("INTERNAL_ERROR", null, "Internal server error", 500);
    return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
        .body(RestResponse.error("Operation failed", List.of(error)));
  }
}
