package eu.europeana.normalization.pids.importer;

import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;
import eu.europeana.normalization.pids.importer.model.Location;
import eu.europeana.normalization.pids.importer.model.PidSchemeLoadable;

/**
 * The interface Persistent identifier scheme importable.
 */
public interface PersistentIdentifierSchemeImportable {
  Iterable<PidSchemeLoadable> importPidSchemes() throws PidSchemeImportException;

  Location getDirectoryLocation();
}
