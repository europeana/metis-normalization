package eu.europeana.normalization.pids;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.http.JvmProxyConfigurer;
import eu.europeana.normalization.util.NormalizationConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PidSchemeVocabularyCachedTest {

  private static final String TEST_PID_ARK = "ark:/12148/bpt6k279983";
  private static final String TEST_PID_URN = "urn:nbn:nl:ui:29-8f66e0a8-b7c9-40a4-be28-54a7c0177061";
  private static PidSchemeVocabularyCached vocabulary;
  private static WireMockServer wireMockServer;

  private static PidSchemeVocabularyCached createTestVocabulary(String sourceUri) throws NormalizationConfigurationException {
    try {
      Constructor<PidSchemeVocabularyCached> constructor =
          PidSchemeVocabularyCached.class.getDeclaredConstructor(String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(sourceUri);
    } catch (Exception e) {
      throw new NormalizationConfigurationException("Failed to create test vocabulary instance", e);
    }
  }

  private static String loadResourceContent(String value) throws IOException {
    try (InputStream inputStream = PidSchemeVocabularyCachedTest.class.getClassLoader()
        .getResourceAsStream("pidTestSchemes/" + value)) {
      return new String(Objects.requireNonNull(inputStream).readAllBytes());
    }
  }

  private static Stream<Arguments> providedPidSchemeInformation() {
    return Stream.of(
        Arguments.of("ark:/12148/bpt6k279983", "ark:/12148/bpt6k279983", "https://n2t.net/ark:/12148/bpt6k279983",
            "http://data.europeana.eu/scheme/pid/ark"),
        Arguments.of("urn:nbn:nl:ui:29-8f66e0a8-b7c9-40a4-be28-54a7c0177061",
            "urn:nbn:nl:ui:29-8f66e0a8-b7c9-40a4-be28-54a7c0177061",
            "https://persistent-identifier.nl/urn:nbn:nl:ui:29-8f66e0a8-b7c9-40a4-be28-54a7c0177061",
            "http://data.europeana.eu/scheme/pid/nbn:nl")
    );
  }

  private static Stream<Arguments> matchPidsSchemePaths() {
    return Stream.of(
        Arguments.of(TEST_PID_ARK, "ark:/12148/bpt6k279983", null), // valid pid
        Arguments.of("https://n2t.net/ark:/12148/bpt6k279984", "ark:/12148/bpt6k279984", null), //valid pid
        Arguments.of(TEST_PID_URN, "urn:nbn:nl:ui:29-8f66e0a8-b7c9-40a4-be28-54a7c0177061", null), //valid pid
        Arguments.of("invalid:pid:that:does:not:match", null, null), //invalid pid
        Arguments.of("not:a:valid:pid", null, null),//invalid pid
        Arguments.of("random:string:1234", null, null),//invalid pid
        Arguments.of("http://example.com", null, null),//invalid pid
        Arguments.of("12345", null, null),//invalid pid
        Arguments.of("", null, null), //empty string is not a valid pid
        Arguments.of("   ", null, null), //whitespace-only string is not a valid pid
        Arguments.of(null, null, NullPointerException.class) // null pid is not a valid pid
    );
  }

  @BeforeAll
  static void setUp() throws NormalizationConfigurationException, IOException {
    String sourceUri = "http://metis-normalization-github.test/directory.yaml";

    wireMockServer = new WireMockServer(wireMockConfig()
        .dynamicPort()
        .enableBrowserProxying(true)
        .notifier(new ConsoleNotifier(true)));
    wireMockServer.start();

    JvmProxyConfigurer.configureFor(wireMockServer);

    wireMockServer.stubFor(get(urlEqualTo("/directory.yaml"))
        .withHost(equalTo("metis-normalization-github.test"))
        .atPriority(1)
        .willReturn(ok().withBody(loadResourceContent("directory.yaml"))));
    wireMockServer.stubFor(get(urlEqualTo("/scheme_a.rdf"))
        .withHost(equalTo("metis-normalization-github.test"))
        .atPriority(1)
        .willReturn(ok().withBody(loadResourceContent("scheme_a.rdf"))));
    wireMockServer.stubFor(get(urlEqualTo("/scheme_b.rdf"))
        .withHost(equalTo("metis-normalization-github.test"))
        .atPriority(1)
        .willReturn(ok().withBody(loadResourceContent("scheme_b.rdf"))));

    // Create a new test-specific vocabulary instance with the test URL
    vocabulary = createTestVocabulary(sourceUri);
  }

  @AfterAll
  static void tearDown() {
    JvmProxyConfigurer.restorePrevious();
    wireMockServer.stop();
  }

  @ParameterizedTest
  @MethodSource("providedPidSchemeInformation")
  void testActualMatcherWithPidSchemes(String pidValue, String canonicalPid, String resolvablePid, String schemeId) {
    // Given
    PidSchemeVocabularyCached pidSchemeVocabulary = vocabulary;

    // When
    final PidMultipleMatchResult normalization = pidSchemeVocabulary.findPids(pidValue);

    // Then
    assertNotNull(normalization);
    assertEquals(Set.of(pidValue), normalization.getOriginalPids());
    assertEquals(canonicalPid, normalization.getCanonicalPid());
    assertEquals(Set.of(resolvablePid), normalization.getResolvablePids());
    assertEquals(schemeId, normalization.getSchemeId());
  }

  @ParameterizedTest(name = "Test matchPid with value: {0}, expecting: {1}, exception: {2}")
  @MethodSource("matchPidsSchemePaths")
  void testMatchPidsPaths(String value, String expected, Class<?> clazz) {
    // When
    if (clazz == null) {
      PidMultipleMatchResult result = vocabulary.findPids(value);

      // Then
      if (expected != null) {
        assertNotNull(result);
        assertTrue(value.contains(result.getOriginalPids().iterator().next()));
        assertEquals(expected, result.getCanonicalPid());
      } else {
        assertNull(result);
      }
    } else {
      assertThrows(NullPointerException.class, () -> vocabulary.findPids(value));
    }
  }

  @Test
  void testMultipleValues() {

    // Two aliases for the same PID
    final String canonicalPid = "ark:/12148/bpt6k279984";
    final String resolvablePid = "https://n2t.net/ark:/12148/bpt6k279984";
    final String multipleIdenticalPIDs = canonicalPid + "," + resolvablePid;
    final PidMultipleMatchResult result = vocabulary.findPids(multipleIdenticalPIDs);
    assertNotNull(result);
    assertEquals(canonicalPid, result.getCanonicalPid());
    assertEquals(1, result.getResolvablePids().size());
    assertEquals(resolvablePid, result.getResolvablePids().iterator().next());
    assertEquals(2, result.getOriginalPids().size());
    assertTrue(result.getOriginalPids().contains(canonicalPid));
    assertTrue(result.getOriginalPids().contains(resolvablePid));

    // Two different PIDs
    final String twoDifferentPids = canonicalPid + "," + TEST_PID_URN;
    assertNull(vocabulary.findPids(twoDifferentPids));
  }

  @Test
  void testCacheInitialization() {
    PidSchemeVocabularyCached pidSchemeVocabularyCached = vocabulary;
    assertNotNull(pidSchemeVocabularyCached);

    // Verify that schemes are loaded
    PidMultipleMatchResult result = pidSchemeVocabularyCached.findPids(TEST_PID_ARK);
    assertNotNull(result, "Schemes should be initialized and able to match PIDs");
  }


  @Test
  void testConcurrentAccess() throws InterruptedException {
    // Given
    final int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    List<PidMultipleMatchResult> results = Collections.synchronizedList(new ArrayList<>());
    List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

    // When
    for (int i = 0; i < threadCount; i++) {
      new Thread(() -> {
        try {
          startLatch.await();

          PidMultipleMatchResult result = vocabulary.findPids(TEST_PID_ARK);
          if (result != null) {
            results.add(result);
          }
        } catch (Exception e) {
          exceptions.add(e);
        } finally {
          endLatch.countDown();
        }
      }).start();
    }
    startLatch.countDown();
    boolean completed = endLatch.await(30, TimeUnit.SECONDS);

    // Then
    assertTrue(completed, "All threads should complete within timeout");
    assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent access");
    assertEquals(threadCount, results.size(), "All threads should successfully get results");
  }

  @Test
  void testMultipleFindCalls() {
    // Test multiple different PIDs
    PidMultipleMatchResult result1 = vocabulary.findPids(TEST_PID_ARK);
    assertNotNull(result1);

    PidMultipleMatchResult result2 = vocabulary.findPids(TEST_PID_URN);
    assertNotNull(result2);

    // Test invalid PID
    PidMultipleMatchResult result3 = vocabulary.findPids("invalid:pid");
    assertNull(result3);

    // Verify results are correct
    assertEquals(Set.of(TEST_PID_ARK), result1.getOriginalPids());
    assertEquals(Set.of(TEST_PID_URN), result2.getOriginalPids());
  }

  @Test
  void testMatcherFunctionBehavior() {

    // When
    // Test multiple applications
    PidMultipleMatchResult result1 = vocabulary.findPids(TEST_PID_ARK);
    assertNotNull(result1);

    PidMultipleMatchResult result2 = vocabulary.findPids(TEST_PID_URN);
    assertNotNull(result2);

    // Then Verify matcher can be reused
    PidMultipleMatchResult result3 = vocabulary.findPids(TEST_PID_ARK);
    assertNotNull(result3);
    assertEquals(result1.getOriginalPids(), result3.getOriginalPids());
  }

  @Test
  void testSchemeLoadingFromImporter() {

    // When
    PidMultipleMatchResult arkResult = vocabulary.findPids(TEST_PID_ARK);

    // Then
    assertNotNull(arkResult);
    assertNotNull(arkResult.getSchemeId());
    assertNotNull(arkResult.getCanonicalPid());
    assertNotNull(arkResult.getResolvablePids());
    assertTrue(arkResult.getResolvablePids().iterator().next().contains("n2t.net"));
  }

  @Test
  void testCanonicalizationProcessing() {
    // When
    PidMultipleMatchResult result = vocabulary.findPids("https://n2t.net/ark:/12148/bpt6k279983");

    // Then
    assertNotNull(result);
    assertEquals("ark:/12148/bpt6k279983", result.getCanonicalPid());
  }

  @Test
  void testResolvablePidGeneration() {

    // When
    PidMultipleMatchResult result = vocabulary.findPids(TEST_PID_ARK);

    // Then
    assertNotNull(result);
    assertTrue(result.getResolvablePids().iterator().next().startsWith("https://"));
    assertTrue(result.getResolvablePids().iterator().next().contains("ark"));
  }

  @Test
  void testCaseInsensitivePidMatching() {
    // When
    // Test that matching is case-insensitive (depends on scheme patterns)
    PidMultipleMatchResult result1 = vocabulary.findPids(TEST_PID_ARK);
    assertNotNull(result1);

    // Then Test with uppercase variations
    PidMultipleMatchResult result2 = vocabulary.findPids(TEST_PID_ARK.toUpperCase());
    // May or may not match depending on scheme definition
    assertNotNull(result2);
  }

  @Test
  void testManySequentialMatches() {
    for (int i = 0; i < 100; i++) {
      // When & Then Test multiple sequential matches
      PidMultipleMatchResult result = vocabulary.findPids(TEST_PID_ARK);
      assertNotNull(result, "Sequential matches should all succeed");
    }
  }

  @Test
  void testDifferentPidFormats() {
    // When
    // Test ARK format
    PidMultipleMatchResult arkResult = vocabulary.findPids("ark:/12148/bpt6k279983");
    assertNotNull(arkResult);

    // Test URN format
    PidMultipleMatchResult urnResult = vocabulary.findPids("urn:nbn:nl:ui:29-8f66e0a8-b7c9-40a4-be28-54a7c0177061");
    assertNotNull(urnResult);

    // Then Verify they belong to different schemes
    assertNotEquals(arkResult.getSchemeId(), urnResult.getSchemeId());
  }

  @Test
  void testSchemeConsistency() {
    // Given Multiple retrievals of matcher should produce consistent results
    // When
    PidMultipleMatchResult result1 = vocabulary.findPids(TEST_PID_ARK);
    PidMultipleMatchResult result2 = vocabulary.findPids(TEST_PID_ARK);

    // Then
    assertEquals(result1.getOriginalPids(), result2.getOriginalPids());
    assertEquals(result1.getCanonicalPid(), result2.getCanonicalPid());
    assertEquals(result1.getSchemeId(), result2.getSchemeId());
  }


  @Test
  void testPerformanceWithFrequentMatching() {
    // When
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
      vocabulary.findPids(TEST_PID_ARK);
    }
    long endTime = System.currentTimeMillis();

    // Then Should complete quickly (well under 1 second for 1000 iterations)
    assertTrue((endTime - startTime) < 1000, "Performance should be good for repeated matches");
  }


  @Test
  void testUrlPidFormatVariations() {

    // When & Then Test different URL variations
    // Test ARK with https URL
    PidMultipleMatchResult result1 = vocabulary.findPids("https://n2t.net/ark:/12148/bpt6k279983");
    assertNotNull(result1);
    assertEquals("ark:/12148/bpt6k279983", result1.getCanonicalPid());

    // Test ARK with http URL
    PidMultipleMatchResult result2 = vocabulary.findPids("http://n2t.net/ark:/12148/bpt6k279983");
    assertNotNull(result2);
    assertEquals("ark:/12148/bpt6k279983", result2.getCanonicalPid());
  }

  @Test
  void testSchemeIdConsistency() {
    // When
    PidMultipleMatchResult result1 = vocabulary.findPids(TEST_PID_ARK);
    String schemeId1 = result1.getSchemeId();

    PidMultipleMatchResult result2 = vocabulary.findPids(TEST_PID_ARK);
    String schemeId2 = result2.getSchemeId();

    // Then
    assertEquals(schemeId1, schemeId2);
  }

  @Test
  void testCanonicalPidConsistency() {

    // When
    String original = "https://n2t.net/ark:/12148/bpt6k279983";
    PidMultipleMatchResult result1 = vocabulary.findPids(original);
    PidMultipleMatchResult result2 = vocabulary.findPids(original);

    // Then
    assertEquals(result1.getCanonicalPid(), result2.getCanonicalPid());
  }

  @Test
  void testResolvablePidConsistency() {

    // When
    String pid = TEST_PID_ARK;
    PidMultipleMatchResult result1 = vocabulary.findPids(pid);
    PidMultipleMatchResult result2 = vocabulary.findPids(pid);

    // Then
    assertEquals(result1.getResolvablePids(), result2.getResolvablePids());
  }

  @Test
  void testMatchingMultipleSchemes() {

    // Match ARK scheme
    PidMultipleMatchResult arkResult = vocabulary.findPids(TEST_PID_ARK);
    assertNotNull(arkResult);

    // Match URN scheme
    PidMultipleMatchResult urnResult = vocabulary.findPids(TEST_PID_URN);
    assertNotNull(urnResult);

    // Verify they're different schemes
    assertNotEquals(
        arkResult.getSchemeId(),
        urnResult.getSchemeId()
    );
  }

  @Test
  void testLongRunningFindLoop() {

    // Test that repeated matches don't cause issues
    for (int i = 0; i < 500; i++) {
      // When
      PidMultipleMatchResult result = vocabulary.findPids(TEST_PID_ARK);
      // Then
      assertNotNull(result);
      assertEquals(Set.of(TEST_PID_ARK), result.getOriginalPids());
    }
  }

  @Test
  void testNoMemoryLeaksWithRepeatedMatching() {

    // When Perform many matches to ensure no memory issues
    for (int i = 0; i < 500000; i++) {
      vocabulary.findPids(TEST_PID_ARK);
      vocabulary.findPids(TEST_PID_URN);
      vocabulary.findPids("invalid:pid:" + i);
    }

    // Then, If we get here without OutOfMemory, test passes
    assertNotNull(vocabulary);
  }

  @Test
  void testConcurrentMatchingWithDifferentPids() throws InterruptedException {
    // Given
    final int threadCount = 8;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

    String[] pids = {TEST_PID_ARK, TEST_PID_URN, "invalid:pid", ""};
    // When
    for (int i = 0; i < threadCount; i++) {
      final int pidIndex = i % pids.length;
      new Thread(() -> {
        try {
          startLatch.await();

          vocabulary.findPids(pids[pidIndex]);
        } catch (NullPointerException e) {
          // Expected for some edge cases
        } catch (Exception e) {
          exceptions.add(e);
        } finally {
          endLatch.countDown();
        }
      }).start();
    }
    startLatch.countDown();
    boolean completed = endLatch.await(30, TimeUnit.SECONDS);
    // Then
    assertTrue(completed, "All threads should complete within timeout");
    assertTrue(exceptions.isEmpty(), "No unexpected exceptions should occur");
  }

  @Test
  void testThreadSafetyWithCacheAccess() throws InterruptedException {
    // Given
    final int threadCount = 20;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    List<String> results = Collections.synchronizedList(new ArrayList<>());

    // When
    for (int i = 0; i < threadCount; i++) {
      new Thread(() -> {
        try {
          startLatch.await();

          PidMultipleMatchResult result = vocabulary.findPids(TEST_PID_ARK);
          if (result != null) {
            results.add(result.getCanonicalPid());
          }
        } catch (Exception e) {
          // Suppress
        } finally {
          endLatch.countDown();
        }
      }).start();
    }
    startLatch.countDown();
    boolean completed = endLatch.await(30, TimeUnit.SECONDS);

    // Then
    assertTrue(completed);
    assertEquals(threadCount, results.size());
  }
}
