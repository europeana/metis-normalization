package eu.europeana.normalization.pids.importer;

import eu.europeana.normalization.pids.PersistentIdentifierSchemes;
import eu.europeana.normalization.pids.PidScheme;
import eu.europeana.normalization.pids.importer.exception.BadContentException;
import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;
import eu.europeana.normalization.pids.importer.model.Location;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

/**
 * The type Persistent identifier scheme importer.
 */
public record PersistentIdentifierSchemeImporter(Location directoryLocation) implements PersistentIdentifierSchemeImportable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public List<PidScheme> importPidSchemes() throws PidSchemeImportException {
    // Obtain the directory entries.
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    final PidSchemeReferencesConfiguration referencesConfiguration;

    try (final InputStream input = directoryLocation.read()) {
      String referenceData = IOUtils.toString(input, StandardCharsets.UTF_8);
      referencesConfiguration = mapper.readValue(referenceData, PidSchemeReferencesConfiguration.class);
    } catch (IOException e) {
      throw new PidSchemeImportException(
          "Could not read configuration directory at [" + directoryLocation + "].", e);
    }

    // Load the pid schemes
    final List<PidScheme> importedSchemes = new ArrayList<>();
    for (String reference : referencesConfiguration.getPidSchemeEntries()) {
      final Location pidSchemeLocation;
      try {
        pidSchemeLocation = directoryLocation.resolve(reference);
      } catch (BadContentException e) {
        throw new PidSchemeImportException(
            String.format("Could not read pid scheme reference at [%s] value [%s].",
                directoryLocation, reference), e);
      }
      try {
        PidScheme pidScheme = loadPersistentIdentifierScheme(pidSchemeLocation);
        if (pidScheme == null) {
          LOGGER.warn("Skipping null PID scheme from importer");
          continue;
        }
        importedSchemes.add(pidScheme);
      } catch (PidSchemeImportException exception) {
        LOGGER.warn("Failed to load individual PID scheme skipping it, continuing with others", exception);
      }
    }

    return importedSchemes;
  }

  /**
   * Gets the directory location.
   *
   * @return the directory location
   */
  @Override
  public Location getDirectoryLocation() {
    return directoryLocation;
  }

  /**
   * Load persistent identifier scheme pid scheme.
   *
   * @param pidSchemeLocation the mapping location
   * @return the pid scheme
   * @throws PidSchemeImportException the pid scheme import exception
   */
  private PidScheme loadPersistentIdentifierScheme(Location pidSchemeLocation) throws PidSchemeImportException {
    // Read the Scheme file.
    final PidScheme persistentIdentifierScheme;
    final XmlMapper xmlMapper = new XmlMapper();
    try (final InputStream input = pidSchemeLocation.read()) {
      persistentIdentifierScheme = xmlMapper
          .readValue(input, PersistentIdentifierSchemes.class)
          .getSchemes()
          .stream()
          .map(PidScheme::new)
          .findFirst()
          .orElseThrow(() -> new PidSchemeImportException(
              "No pid scheme found in file at [" + pidSchemeLocation + "]."));
    } catch (IOException e) {
      throw new PidSchemeImportException(
          "Could not read pid scheme at [" + pidSchemeLocation + "].", e);
    }
    // return the scheme
    return persistentIdentifierScheme;
  }
}
