package eu.europeana.normalization.pids.importer.exception;

/**
 * Indicates an issue with importing a PID scheme or the PID scheme collection.
 */
public class PidSchemeImportException extends Exception {

  private static final long serialVersionUID = -3254360258206690287L;

  /**
   * Constructor
   *
   * @param message The message.
   */
  public PidSchemeImportException(String message) {
    super(message);
  }

  /**
   * Constructor.
   *
   * @param message The message.
   * @param cause The cause.
   */
  public PidSchemeImportException(String message, Throwable cause) {
    super(message, cause);
  }

}
