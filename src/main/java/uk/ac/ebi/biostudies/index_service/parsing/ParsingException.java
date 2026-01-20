package uk.ac.ebi.biostudies.index_service.parsing;

/**
 * Runtime exception to signal errors encountered during parsing operations.
 * This unchecked exception wraps the root cause of parsing failures.
 */
public class ParsingException extends RuntimeException {

  /**
   * Constructs a new ParsingException with the specified detail message.
   *
   * @param message the detail message explaining the exception
   */
  public ParsingException(String message) {
    super(message);
  }

  /**
   * Constructs a new ParsingException with the specified detail message and cause.
   *
   * @param message the detail message explaining the exception
   * @param cause the original exception that caused this parsing failure
   */
  public ParsingException(String message, Throwable cause) {
    super(message, cause);
  }
}
