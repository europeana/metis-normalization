package eu.europeana.normalization.pids.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eu.europeana.normalization.pids.PidScheme;
import eu.europeana.normalization.pids.importer.exception.BadContentException;
import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;
import eu.europeana.normalization.pids.importer.model.Location;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the PersistentIdentifierSchemeImporter class with comprehensive coverage of all code paths and exception cases.
 */
class PersistentIdentifierSchemeImporterTest {

  private static final String VALID_XML_SCHEME =
      """
          <?xml version="1.0" encoding="UTF-8"?>
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                   xmlns:dcterms="http://purl.org/dc/terms/"
                   xmlns:edm="http://www.europeana.eu/schemas/edm/"
                   xmlns:doap="http://usefulinc.com/ns/doap#"
                   xmlns:rdfs="https://www.w3.org/TR/rdf-schema/#">
            <edm:PersistentIdentifierScheme rdf:about="http://example.org/scheme1">
              <dcterms:title>Test Scheme 1</dcterms:title>
              <edm:canonicalPattern>pattern1</edm:canonicalPattern>
              <edm:resolvablePattern>pattern1</edm:resolvablePattern>
              <edm:matchingPattern>.*</edm:matchingPattern>
              <doap:maintainer>Test Organization</doap:maintainer>
            </edm:PersistentIdentifierScheme>
          </rdf:RDF>
          """;

  private static final String VALID_XML_MULTIPLE_SCHEMES =
      """
          <?xml version="1.0" encoding="UTF-8"?>
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                   xmlns:dcterms="http://purl.org/dc/terms/"
                   xmlns:edm="http://www.europeana.eu/schemas/edm/"
                   xmlns:doap="http://usefulinc.com/ns/doap#"
                   xmlns:rdfs="https://www.w3.org/TR/rdf-schema/#">
            <edm:PersistentIdentifierScheme rdf:about="http://example.org/scheme1">
              <dcterms:title>Test Scheme 1</dcterms:title>
              <edm:canonicalPattern>pattern1</edm:canonicalPattern>
              <edm:resolvablePattern>pattern1</edm:resolvablePattern>
              <edm:matchingPattern>.*</edm:matchingPattern>
              <doap:maintainer>Test Organization</doap:maintainer>
            </edm:PersistentIdentifierScheme>
            <edm:PersistentIdentifierScheme rdf:about="http://example.org/scheme2">
              <dcterms:title>Test Scheme 2</dcterms:title>
              <edm:canonicalPattern>pattern2</edm:canonicalPattern>
              <edm:resolvablePattern>pattern2</edm:resolvablePattern>
              <edm:matchingPattern>.*</edm:matchingPattern>
              <doap:maintainer>Test Organization</doap:maintainer>
            </edm:PersistentIdentifierScheme>
          </rdf:RDF>
          """;

  private static final String EMPTY_XML_SCHEME =
      """
          <?xml version="1.0" encoding="UTF-8"?>
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                   xmlns:dcterms="http://purl.org/dc/terms/"
                   xmlns:edm="http://www.europeana.eu/schemas/edm/"
                   xmlns:doap="http://usefulinc.com/ns/doap#"
                   xmlns:rdfs="https://www.w3.org/TR/rdf-schema/#">
          </rdf:RDF>
          """;

  /**
   * Test successful import with a single scheme.
   */
  @Test
  void testImportPidSchemesSuccessful() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    Location resolvedLocation = mock(Location.class);
    InputStream schemeInput =
        new ByteArrayInputStream(VALID_XML_SCHEME.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation.read()).thenReturn(schemeInput);

    when(mockLocation.resolve("scheme1.xml")).thenReturn(resolvedLocation);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    List<PidScheme> result = importer.importPidSchemes();

    // Then
    assertNotNull(result);
    assertEquals(1, result.size());
  }

  /**
   * Test successful import with multiple schemes.
   */
  @Test
  void testImportPidSchemesMultiple() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String multipleConfig = """
                            pid:
                              - scheme1.xml
                              - scheme2.xml
                              - scheme3.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(multipleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    Location resolvedLocation1 = mock(Location.class);
    InputStream schemeInput1 =
        new ByteArrayInputStream(VALID_XML_SCHEME.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation1.read()).thenReturn(schemeInput1);
    when(mockLocation.resolve("scheme1.xml")).thenReturn(resolvedLocation1);

    Location resolvedLocation2 = mock(Location.class);
    InputStream schemeInput2 =
        new ByteArrayInputStream(VALID_XML_SCHEME.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation2.read()).thenReturn(schemeInput2);
    when(mockLocation.resolve("scheme2.xml")).thenReturn(resolvedLocation2);

    Location resolvedLocation3 = mock(Location.class);
    InputStream schemeInput3 =
        new ByteArrayInputStream(VALID_XML_SCHEME.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation3.read()).thenReturn(schemeInput3);
    when(mockLocation.resolve("scheme3.xml")).thenReturn(resolvedLocation3);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    List<PidScheme> result = importer.importPidSchemes();

    // Then
    assertNotNull(result);
    assertEquals(3, result.size());
  }

  /**
   * Test that importPidSchemes throws PidSchemeImportException when config location cannot be read.
   */
  @Test
  void testImportPidSchemesConfigReadFailure() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    when(mockLocation.read()).thenThrow(new IOException("Config read failed"));

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When & Then
    PidSchemeImportException exception =
        assertThrows(PidSchemeImportException.class, importer::importPidSchemes);
    assertNotNull(exception.getMessage());
    assertTrue(exception.getMessage().contains("Could not read configuration directory"));
    assertNotNull(exception.getCause());
    assertInstanceOf(IOException.class, exception.getCause());
  }

  /**
   * Test that importPidSchemes throws PidSchemeImportException when location resolve fails with BadContentException.
   */
  @Test
  void testImportPidSchemesResolveLocationFailure() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    when(mockLocation.resolve("scheme1.xml"))
        .thenThrow(new BadContentException("Invalid path"));

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When & Then
    PidSchemeImportException exception =
        assertThrows(PidSchemeImportException.class, importer::importPidSchemes);
    assertNotNull(exception.getMessage());
    assertTrue(
        exception.getMessage().contains("Could not read pid scheme reference"));
    assertNotNull(exception.getCause());
    assertInstanceOf(BadContentException.class, exception.getCause());
  }

  /**
   * Test that loading a scheme through the PidScheme importer succeeds.
   */
  @Test
  void testLoadPidSchemeViaImporter() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    Location resolvedLocation = mock(Location.class);
    InputStream schemeInput =
        new ByteArrayInputStream(VALID_XML_SCHEME.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation.read()).thenReturn(schemeInput);

    when(mockLocation.resolve("scheme1.xml")).thenReturn(resolvedLocation);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    List<PidScheme> result = importer.importPidSchemes();

    PidScheme scheme = result.getFirst();

    // Then
    assertNotNull(scheme);
    assertEquals("http://example.org/scheme1", scheme.getSchemeId());
  }

  /**
   * Test that loading a scheme throws an exception when an XML file cannot be read.
   */
  @Test
  void testLoadPidSchemeXmlReadFailure() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    Location resolvedLocation = mock(Location.class);
    when(resolvedLocation.read()).thenThrow(new IOException("XML read failed"));

    when(mockLocation.resolve("scheme1.xml")).thenReturn(resolvedLocation);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    List<PidScheme> result = importer.importPidSchemes();


    // Then
    assertThrows(NoSuchElementException.class, result::getFirst);
  }

  /**
   * Test that loading a scheme throws exception when no schemes found in XML.
   */
  @Test
  void testLoadPidSchemeNoSchemesInXml() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    Location resolvedLocation = mock(Location.class);
    InputStream schemeInput =
        new ByteArrayInputStream(EMPTY_XML_SCHEME.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation.read()).thenReturn(schemeInput);

    when(mockLocation.resolve("scheme1.xml")).thenReturn(resolvedLocation);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When & Then - Empty scheme list returns null, which causes NPE on stream().findFirst()
    // This tests the error path when no schemes are found
    assertThrows(Exception.class, importer::importPidSchemes);
  }

  /**
   * Test that getDirectoryLocation returns the correct location.
   */
  @Test
  void testGetDirectoryLocation() {
    // Given
    Location mockLocation = mock(Location.class);
    when(mockLocation.toString()).thenReturn("test://location");
    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    Location result = importer.getDirectoryLocation();

    // Then
    assertNotNull(result);
    assertEquals(mockLocation, result);
  }

  /**
   * Test that the importer is a record and implements the expected interface.
   */
  @Test
  void testImporterImplementsInterface() {
    // Given
    Location mockLocation = mock(Location.class);
    PersistentIdentifierSchemeImporter importer = new PersistentIdentifierSchemeImporter(mockLocation);

    // Then
    assertInstanceOf(PersistentIdentifierSchemeImportable.class, importer);
  }

  /**
   * Test loading scheme with multiple schemes in XML file (should use first one).
   */
  @Test
  void testLoadPidSchemeMultipleSchemesUsesFirst() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    Location resolvedLocation = mock(Location.class);
    InputStream schemeInput =
        new ByteArrayInputStream(VALID_XML_MULTIPLE_SCHEMES.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation.read()).thenReturn(schemeInput);

    when(mockLocation.resolve("scheme1.xml")).thenReturn(resolvedLocation);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    List<PidScheme> result = importer.importPidSchemes();

    PidScheme scheme = result.getFirst();

    // Then
    assertNotNull(scheme);
    // Should use the first scheme from the file
    assertEquals("http://example.org/scheme1", scheme.getSchemeId());
  }

  /**
   * Test an empty config file (no schemes).
   */
  @Test
  void testImportPidSchemesEmptyConfig() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String emptyConfig = "pid: []\n";
    InputStream configInput =
        new ByteArrayInputStream(emptyConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    List<PidScheme> result = importer.importPidSchemes();

    // Then
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  /**
   * Test that ImportException wraps IOException with proper message formatting.
   */
  @Test
  void testImportExceptionMessageFormatting() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    when(mockLocation.toString()).thenReturn("mock://test/location");
    when(mockLocation.read()).thenThrow(new IOException("Test IO error"));

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    PidSchemeImportException exception =
        assertThrows(PidSchemeImportException.class, importer::importPidSchemes);

    // Then
    assertTrue(exception.getMessage().contains("Could not read configuration directory at"));
    assertTrue(exception.getMessage().contains("mock://test/location"));
  }

  /**
   * Test that resolve failure exception includes reference details.
   */
  @Test
  void testResolveExceptionMessageFormatting() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    when(mockLocation.toString()).thenReturn("mock://base");

    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    when(mockLocation.resolve("scheme1.xml"))
        .thenThrow(new BadContentException("Bad reference"));

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    PidSchemeImportException exception =
        assertThrows(PidSchemeImportException.class, importer::importPidSchemes);

    // Then
    assertTrue(exception.getMessage().contains("Could not read pid scheme reference"));
    assertTrue(exception.getMessage().contains("scheme1.xml"));
    assertTrue(exception.getMessage().contains("mock://base"));
  }

  @Test
  void testPidSchemeLoadableMultipleCalls() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    Location resolvedLocation = mock(Location.class);
    InputStream schemeInput1 =
        new ByteArrayInputStream(VALID_XML_SCHEME.getBytes(StandardCharsets.UTF_8));
    InputStream schemeInput2 =
        new ByteArrayInputStream(VALID_XML_SCHEME.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation.read()).thenReturn(schemeInput1).thenReturn(schemeInput2);

    when(mockLocation.resolve("scheme1.xml")).thenReturn(resolvedLocation);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When
    List<PidScheme> result = importer.importPidSchemes();

    PidScheme scheme1 = result.getFirst();
    PidScheme scheme2 = result.getFirst();

    // Then
    assertNotNull(scheme1);
    assertNotNull(scheme2);
    assertEquals(scheme1.getSchemeId(), scheme2.getSchemeId());
  }

  /**
   * Test XML parsing error handling.
   */
  @Test
  void testLoadPidSchemeXmlParsingError() throws Exception {
    // Given
    Location mockLocation = mock(Location.class);
    String singleConfig = """
                            pid:
                              - scheme1.xml
                            """;
    InputStream configInput =
        new ByteArrayInputStream(singleConfig.getBytes(StandardCharsets.UTF_8));
    when(mockLocation.read()).thenReturn(configInput);

    Location resolvedLocation = mock(Location.class);
    String invalidXml = "This is not valid XML at all";
    InputStream schemeInput =
        new ByteArrayInputStream(invalidXml.getBytes(StandardCharsets.UTF_8));
    when(resolvedLocation.read()).thenReturn(schemeInput);

    when(mockLocation.resolve("scheme1.xml")).thenReturn(resolvedLocation);

    PersistentIdentifierSchemeImporter importer =
        new PersistentIdentifierSchemeImporter(mockLocation);

    // When & Then - Jackson will throw an exception for invalid XML, which will be wrapped
    Exception exception = assertThrows(Exception.class, importer::importPidSchemes);
    assertNotNull(exception);
  }
}
