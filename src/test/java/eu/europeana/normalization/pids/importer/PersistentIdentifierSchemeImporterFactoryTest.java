package eu.europeana.normalization.pids.importer;

import static org.junit.jupiter.api.Assertions.*;

import eu.europeana.normalization.pids.importer.exception.BadContentException;
import eu.europeana.normalization.pids.importer.model.Location;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The type Persistent identifier scheme importer factory test.
 */
class PersistentIdentifierSchemeImporterFactoryTest {

  private static final String TEST_CONTENT = "Test content";

  /**
   * Test create importer with url.
   *
   * @throws MalformedURLException the malformed url exception
   */
  @Test
  void testCreateImporterWithURL() throws MalformedURLException {
    // Given
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    URL testUrl = new URL("file:///test/path");

    // When
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testUrl);

    // Then
    assertNotNull(importer);
    assertNotNull(importer.getDirectoryLocation());
  }

  /**
   * Test create importer with path.
   *
   * @param tempDir the temp dir
   */
  @Test
  void testCreateImporterWithPath(@TempDir Path tempDir) {
    // Given
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();

    // When
    PersistentIdentifierSchemeImporter importer = factory.createImporter(tempDir);

    // Then
    assertNotNull(importer);
    assertNotNull(importer.getDirectoryLocation());
  }

  /**
   * Test create importer with base directory and path.
   *
   * @param baseDir the base dir
   * @param contentDir the content dir
   */
  @Test
  void testCreateImporterWithBaseDirectoryAndPath(@TempDir Path baseDir, @TempDir Path contentDir) {
    // Given
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();

    // When
    PersistentIdentifierSchemeImporter importer = factory.createImporter(baseDir, contentDir);

    // Then
    assertNotNull(importer);
    assertNotNull(importer.getDirectoryLocation());
  }

  /**
   * Test create importer with null base directory.
   *
   * @param contentDir the content dir
   */
  @Test
  void testCreateImporterWithNullBaseDirectory(@TempDir Path contentDir) {
    // Given
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();

    // When
    PersistentIdentifierSchemeImporter importer = factory.createImporter(null, contentDir);

    // Then
    assertNotNull(importer);
    assertNotNull(importer.getDirectoryLocation());
  }

  /**
   * Test url location read.
   *
   * @throws Exception the exception
   */
  @Test
  void testUrlLocationRead() throws Exception {
    // Given
    URL testUrl = new URL("file:///dev/null");
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testUrl);
    Location location = importer.getDirectoryLocation();

    // When
    InputStream stream = location.read();

    // Then
    assertNotNull(stream);
    stream.close();
  }

  /**
   * Test url location to string.
   *
   * @throws MalformedURLException the malformed url exception
   */
  @Test
  void testUrlLocationToString() throws MalformedURLException {
    // Given
    String expectedUrl = "file:///test/path";
    URL testUrl = URI.create(expectedUrl).toURL();
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testUrl);
    Location location = importer.getDirectoryLocation();

    // When
    String result = location.toString();

    // Then
    assertNotNull(result);
    // URL.toString() normalizes the format, so we just check it contains key parts
    assertTrue(result.contains("file:") && result.contains("test") && result.contains("path"));
  }

  /**
   * Test url location resolve with valid relative path.
   *
   * @throws Exception the exception
   */
  @Test
  void testUrlLocationResolveWithValidRelativePath() throws Exception {
    // Given
    URL testUrl = URI.create("file:///test/config.yaml").toURL();
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testUrl);
    Location location = importer.getDirectoryLocation();

    // When
    Location resolvedLocation = location.resolve("subdir/file.yaml");

    // Then
    assertNotNull(resolvedLocation);
    assertNotNull(resolvedLocation.toString());
    assertTrue(resolvedLocation.toString().contains("subdir"));
  }

  /**
   * Test url location resolve with invalid url.
   *
   * @throws Exception the exception
   */
  @Test
  void testUrlLocationResolveWithInvalidURL() throws Exception {
    // Given
    URL testUrl = URI.create("file:///test/config.yaml").toURL();
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testUrl);
    Location location = importer.getDirectoryLocation();

    // When & Then
    assertThrows(BadContentException.class, () -> location.resolve("\u0000invalid"));
  }

  /**
   * Test url location resolve with malformed relative path.
   *
   * @throws Exception the exception
   */
  @Test
  void testUrlLocationResolveWithMalformedRelativePath() throws Exception {
    // Given
    URL testUrl = new URL("file:///test/config.yaml");
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testUrl);
    Location location = importer.getDirectoryLocation();

    // When & Then
    // Null character is invalid and should throw BadContentException
    assertThrows(BadContentException.class, () -> location.resolve("\u0000/etc/passwd"));
  }

  /**
   * Test url location resolve relative path.
   *
   * @throws Exception the exception
   */
  @Test
  void testUrlLocationResolveRelativePath() throws Exception {
    // Given
    URL testUrl = new URL("file:///base/config.yaml");
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testUrl);
    Location location = importer.getDirectoryLocation();

    // When
    Location resolved = location.resolve("other.yaml");

    // Then
    assertNotNull(resolved);
    assertTrue(resolved.toString().contains("other.yaml"));
  }

  /**
   * Test path location read with existing file.
   *
   * @param tempDir the temp dir
   * @throws IOException the io exception
   */
  @Test
  void testPathLocationReadWithExistingFile(@TempDir Path tempDir) throws IOException {
    // Given
    Path testFile = tempDir.resolve("test.txt");
    Files.write(testFile, TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testFile);
    Location location = importer.getDirectoryLocation();

    // When
    InputStream stream = location.read();

    // Then
    assertNotNull(stream);
    stream.close();
  }

  /**
   * Test path location read with non existent file.
   *
   * @param tempDir the temp dir
   */
  @Test
  void testPathLocationReadWithNonExistentFile(@TempDir Path tempDir) {
    // Given
    Path testFile = tempDir.resolve("nonexistent.txt");
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testFile);
    Location location = importer.getDirectoryLocation();

    // When & Then
    assertThrows(IOException.class, location::read);
  }

  /**
   * Test path location to string with base directory.
   *
   * @param baseDir the base dir
   * @param contentDir the content dir
   * @throws IOException the io exception
   */
  @Test
  void testPathLocationToStringWithBaseDirectory(@TempDir Path baseDir, @TempDir Path contentDir)
      throws IOException {
    // Given
    Path contentFile = contentDir.resolve("test.txt");
    Files.write(contentFile, TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer =
        factory.createImporter(baseDir, contentFile);
    Location location = importer.getDirectoryLocation();

    // When
    String result = location.toString();

    // Then
    assertNotNull(result);
    assertFalse(result.isEmpty());
  }

  /**
   * Test path location to string without base directory.
   *
   * @param tempDir the temp dir
   * @throws IOException the io exception
   */
  @Test
  void testPathLocationToStringWithoutBaseDirectory(@TempDir Path tempDir) throws IOException {
    // Given
    Path testFile = tempDir.resolve("test.txt");
    Files.write(testFile, TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(null, testFile);
    Location location = importer.getDirectoryLocation();

    // When
    String result = location.toString();

    // Then
    assertEquals(testFile.toString(), result);
  }

  /**
   * Test path location resolve with base directory.
   *
   * @param baseDir the base dir
   * @param contentDir the content dir
   * @throws IOException the io exception
   * @throws BadContentException the bad content exception
   */
  @Test
  void testPathLocationResolveWithBaseDirectory(@TempDir Path baseDir, @TempDir Path contentDir)
      throws IOException, BadContentException {
    // Given
    Path contentFile = contentDir.resolve("config.yaml");
    Files.write(contentFile, TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer =
        factory.createImporter(baseDir, contentFile);
    Location location = importer.getDirectoryLocation();

    // When
    Location resolvedLocation = location.resolve("other.yaml");

    // Then
    assertNotNull(resolvedLocation);
    assertNotNull(resolvedLocation.toString());
  }

  /**
   * Test path location resolve without base directory.
   *
   * @param tempDir the temp dir
   * @throws IOException the io exception
   * @throws BadContentException the bad content exception
   */
  @Test
  void testPathLocationResolveWithoutBaseDirectory(@TempDir Path tempDir) throws IOException, BadContentException {
    // Given
    Path testFile = tempDir.resolve("config.yaml");
    Files.write(testFile, TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(null, testFile);
    Location location = importer.getDirectoryLocation();

    // When
    Location resolvedLocation = location.resolve("other.yaml");

    // Then
    assertNotNull(resolvedLocation);
    assertNotNull(resolvedLocation.toString());
  }

  /**
   * Test path location resolve multiple relative paths.
   *
   * @param tempDir the temp dir
   * @throws IOException the io exception
   * @throws BadContentException the bad content exception
   */
  @Test
  void testPathLocationResolveMultipleRelativePaths(@TempDir Path tempDir) throws IOException, BadContentException {
    // Given
    Path testFile = tempDir.resolve("base/config.yaml");
    Files.createDirectories(testFile.getParent());
    Files.write(testFile, TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testFile);
    Location location = importer.getDirectoryLocation();

    // When
    Location resolved = location.resolve("../other/file.yaml");

    // Then
    assertNotNull(resolved);
    assertNotNull(resolved.toString());
  }

  /**
   * Test factory creates valid importer.
   *
   * @param tempDir the temp dir
   * @throws IOException the io exception
   */
  @Test
  void testFactoryCreatesValidImporter(@TempDir Path tempDir) throws IOException {
    // Given
    Path testFile = tempDir.resolve("test.txt");
    Files.write(testFile, TEST_CONTENT.getBytes(StandardCharsets.UTF_8));
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();

    // When
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testFile);

    // Then
    assertNotNull(importer);
    assertEquals(testFile.toString(), importer.getDirectoryLocation().toString());
  }

  /**
   * Test multiple importers independent.
   *
   * @param tempDir1 the temp dir 1
   * @param tempDir2 the temp dir 2
   * @throws IOException the io exception
   */
  @Test
  void testMultipleImportersIndependent(@TempDir Path tempDir1, @TempDir Path tempDir2)
      throws IOException {
    // Given
    Path file1 = tempDir1.resolve("file1.txt");
    Path file2 = tempDir2.resolve("file2.txt");
    Files.write(file1, "content1".getBytes(StandardCharsets.UTF_8));
    Files.write(file2, "content2".getBytes(StandardCharsets.UTF_8));
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();

    // When
    PersistentIdentifierSchemeImporter importer1 = factory.createImporter(file1);
    PersistentIdentifierSchemeImporter importer2 = factory.createImporter(file2);

    // Then
    assertNotNull(importer1);
    assertNotNull(importer2);
    assertNotEquals(
        importer1.getDirectoryLocation().toString(),
        importer2.getDirectoryLocation().toString());
  }

  /**
   * Test url location resolve preserves path.
   *
   * @throws Exception the exception
   */
  @Test
  void testUrlLocationResolvePreservesPath() throws Exception {
    // Given
    URL testUrl = new URL("file:///base/dir/config.yaml");
    PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
    PersistentIdentifierSchemeImporter importer = factory.createImporter(testUrl);
    Location location = importer.getDirectoryLocation();

    // When
    Location resolved = location.resolve("schemes/scheme1.xml");

    // Then
    String resolvedPath = resolved.toString();
    assertTrue(resolvedPath.contains("schemes"));
    assertTrue(resolvedPath.contains("scheme1.xml"));
  }
}
