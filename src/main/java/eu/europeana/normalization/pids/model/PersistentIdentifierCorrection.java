package eu.europeana.normalization.pids.model;

/**
 * A correction to be applied to PID values. This models the data that is obtained from the external
 * vocabulary of PID corrections. It consists of a match (a regex pattern that the PID to be
 * corrected must satisfy) and a replacement, that determines how a PID value that satisfies the
 * pattern is to be corrected.
 */
public class PersistentIdentifierCorrection {

  private String match;
  private String replace;

  public String getMatch() {
    return match;
  }

  public void setMatch(String match) {
    this.match = match;
  }

  public String getReplace() {
    return replace;
  }

  public void setReplace(String replace) {
    this.replace = replace;
  }
}
