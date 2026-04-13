package eu.europeana.normalization.pids.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class PidSchemeReferencesConfiguration {
  @JsonProperty("pid")
  private List<String> pidSchemeEntries;
  public List<String> getPidSchemeEntries() {
    return pidSchemeEntries;
  }
  void setPidSchemeEntries(List<String> pidSchemeEntries) {
    this.pidSchemeEntries = pidSchemeEntries;
  }
}
