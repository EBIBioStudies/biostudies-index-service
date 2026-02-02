package uk.ac.ebi.biostudies.index_service.exceptions;

/**
 * Umbrella exception for all search-related errors.
 * <p>
 * Wraps lower-level exceptions (IOException, IllegalArgumentException, etc.) to provide
 * a consistent exception hierarchy for search operations. Callers can catch this single
 * exception type instead of handling multiple checked exceptions.
 */
public class SearchException extends RuntimeException {

  public SearchException(String message) {
    super(message);
  }

  public SearchException(String message, Throwable cause) {
    super(message, cause);
  }

  public SearchException(Throwable cause) {
    super(cause);
  }
}