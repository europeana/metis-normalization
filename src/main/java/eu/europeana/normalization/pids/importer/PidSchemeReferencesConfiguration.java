package eu.europeana.normalization.pids.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The type Pid scheme references configuration.
 */
public class PidSchemeReferencesConfiguration {
  @JsonProperty("pid")
  private List<String> pidSchemeEntries;

  /**
   * Gets pid scheme entries.
   *
   * @return the pid scheme entries
   */
  public List<String> getPidSchemeEntries() {
    return List.copyOf(pidSchemeEntries);
  }

  /**
   * Sets pid scheme entries.
   *
   * @param pidSchemeEntries the pid scheme entries
   */
  void setPidSchemeEntries(List<String> pidSchemeEntries) {
    this.pidSchemeEntries = List.copyOf(pidSchemeEntries);
  }
}
