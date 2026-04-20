package eu.europeana.normalization.pids.importer;

import eu.europeana.normalization.pids.PersistentIdentifierSchemes;
import eu.europeana.normalization.pids.PidScheme;
import eu.europeana.normalization.pids.importer.exception.BadContentException;
import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;
import eu.europeana.normalization.pids.importer.model.Location;
import eu.europeana.normalization.pids.importer.model.PidSchemeLoadable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

/**
 * The type Persistent identifier scheme importer.
 */
public record PersistentIdentifierSchemeImporter(Location directoryLocation) implements PersistentIdentifierSchemeImportable {

  @Override
  public Iterable<PidSchemeLoadable> importPidSchemes() throws PidSchemeImportException {
    // Obtain the directory entries.
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    final PidSchemeReferencesConfiguration referencesConfiguration;

    try (final InputStream input = directoryLocation.read()) {
      String information = IOUtils.toString(input, StandardCharsets.UTF_8);
      referencesConfiguration = mapper.readValue(information, PidSchemeReferencesConfiguration.class);
    } catch (IOException e) {
      throw new PidSchemeImportException(
          "Could not read configuration directory at [" + directoryLocation + "].", e);
    }

    // Compile the pid scheme loaders
    final List<PidSchemeLoadable> result = new ArrayList<>();
    for (String reference : referencesConfiguration.getPidSchemeEntries()) {
      final Location mappingLocation;
      try {
        mappingLocation = directoryLocation.resolve(reference);
      } catch (BadContentException e) {
        throw new PidSchemeImportException(
            String.format("Could not read pid scheme reference at [%s] value [%s].",
                directoryLocation, reference), e);
      }
      result.add(() -> loadPersistentIdentifierScheme(mappingLocation));
    }

    // Done
    return result;
  }

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
