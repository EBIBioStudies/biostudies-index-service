package uk.ac.ebi.biostudies.index_service.search.query;

/**
 * Exception thrown when query building fails.
 */
public class QueryBuildException extends RuntimeException {

  public QueryBuildException(String message) {
    super(message);
  }

  public QueryBuildException(String message, Throwable cause) {
    super(message, cause);
  }
}
