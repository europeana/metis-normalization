package eu.europeana.normalization.pids.importer.exception;


public class BadContentException extends Exception {
  private static final long serialVersionUID = 6493765680281572511L;

  public BadContentException(String message) {
    super(message);
  }

  public BadContentException(String message, Throwable cause) {
    super(message, cause);
  }
}
