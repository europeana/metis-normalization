package eu.europeana.normalization.pids.importer.model;

import eu.europeana.normalization.pids.PidScheme;
import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;

@FunctionalInterface
public interface PidSchemeLoadable {

  /**
   * Trigger a loading of the PID scheme. Blocks until the PID scheme is loaded. This method can be
   * called multiple times, but only the first call will trigger an actual loading of the PID scheme.
   *
   * @return The loaded PID scheme.
   * @throws PidSchemeImportException In case there was a problem loading the PID scheme.
   */
  PidScheme load() throws PidSchemeImportException;
}
