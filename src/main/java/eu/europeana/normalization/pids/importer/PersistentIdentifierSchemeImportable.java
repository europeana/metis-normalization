package eu.europeana.normalization.pids.importer;

import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;
import eu.europeana.normalization.pids.importer.model.Location;
import eu.europeana.normalization.pids.importer.model.PidSchemeLoadable;

/**
 * The interface Persistent identifier scheme importable.
 */
public interface PersistentIdentifierSchemeImportable {

  /**
   * Import pid schemes iterable.
   *
   * @return the iterable
   * @throws PidSchemeImportException the pid scheme import exception
   */
  Iterable<PidSchemeLoadable> importPidSchemes() throws PidSchemeImportException;

  /**
   * Gets directory location.
   *
   * @return the directory location
   */
  Location getDirectoryLocation();
}
