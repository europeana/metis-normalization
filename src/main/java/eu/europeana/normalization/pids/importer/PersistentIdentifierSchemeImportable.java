package eu.europeana.normalization.pids.importer;

import eu.europeana.normalization.pids.PidScheme;
import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;
import eu.europeana.normalization.pids.importer.model.Location;
import java.util.List;

/**
 * The interface Persistent identifier scheme importable.
 */
public interface PersistentIdentifierSchemeImportable {


  /**
   * Import pid schemes list.
   *
   * @return the list
   * @throws PidSchemeImportException the pid scheme import exception
   */
  List<PidScheme> importPidSchemes() throws PidSchemeImportException;

  /**
   * Gets directory location.
   *
   * @return the directory location
   */
  Location getDirectoryLocation();
}
