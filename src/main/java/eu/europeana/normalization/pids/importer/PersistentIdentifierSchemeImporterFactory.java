package eu.europeana.normalization.pids.importer;

import eu.europeana.normalization.pids.importer.exception.BadContentException;
import eu.europeana.normalization.pids.importer.model.Location;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The type Persistent identifier scheme importer factory.
 */
public class PersistentIdentifierSchemeImporterFactory {

  /**
   * Create importer persistent identifier scheme importer.
   *
   * @param directoryLocation the directory location
   * @return the persistent identifier scheme importer
   */
  public PersistentIdentifierSchemeImporter createImporter(URL directoryLocation) {
    return new PersistentIdentifierSchemeImporter(new UrlLocation(directoryLocation));
  }

  /**
   * Create importer persistent identifier scheme importer.
   *
   * @param directoryLocation the directory location
   * @return the persistent identifier scheme importer
   */
  public PersistentIdentifierSchemeImporter createImporter(Path directoryLocation) {
    return createImporter(null, directoryLocation);
  }

  /**
   * Create importer persistent identifier scheme importer.
   *
   * @param baseDirectory the base directory
   * @param directoryLocation the directory location
   * @return the persistent identifier scheme importer
   */
  public PersistentIdentifierSchemeImporter createImporter(Path baseDirectory, Path directoryLocation) {
    return new PersistentIdentifierSchemeImporter(new PathLocation(baseDirectory, directoryLocation));
  }

  private static final class UrlLocation implements Location {

    private final URL url;

    /**
     * Instantiates a new Url location.
     *
     * @param url the url
     */
    UrlLocation(URL url) {
      this.url = url;
    }

    @Override
    public InputStream read() throws IOException {
      return url.openStream();
    }

    @Override
    public Location resolve(String relativeLocation) throws BadContentException {
      try {
        return new UrlLocation(url.toURI().resolve(relativeLocation).toURL());
      } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
        throw new BadContentException(
            String.format("Provided url '%s' and relative location %s, failed to parse.", url, relativeLocation), e);
      }
    }

    @Override
    public String toString() {
      return url.toString();
    }
  }

  private static final class PathLocation implements Location {

    private final Path baseDirectory;
    private final Path fullPath;

    /**
     * Instantiates a new Path location.
     *
     * @param baseDirectory the base directory
     * @param fullPath the full path
     */
    PathLocation(Path baseDirectory, Path fullPath) {
      this.baseDirectory = baseDirectory;
      this.fullPath = fullPath;
    }

    @Override
    public InputStream read() throws IOException {
      return Files.newInputStream(fullPath);
    }

    @Override
    public Location resolve(String relativeLocation) {
      return new PathLocation(baseDirectory, fullPath.getParent().resolve(relativeLocation));
    }

    @Override
    public String toString() {
      return (baseDirectory == null ? fullPath : baseDirectory.relativize(fullPath)).toString();
    }
  }
}
