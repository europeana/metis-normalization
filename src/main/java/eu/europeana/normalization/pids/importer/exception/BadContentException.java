package eu.europeana.normalization.pids.importer.exception;


/**
 * The type Bad content exception.
 */
public class BadContentException extends Exception {
  private static final long serialVersionUID = 6493765680281572511L;

  /**
   * Instantiates a new Bad content exception.
   *
   * @param message the message
   */
  public BadContentException(String message) {
    super(message);
  }

  /**
   * Instantiates a new Bad content exception.
   *
   * @param message the message
   * @param cause the cause
   */
  public BadContentException(String message, Throwable cause) {
    super(message, cause);
  }
}
