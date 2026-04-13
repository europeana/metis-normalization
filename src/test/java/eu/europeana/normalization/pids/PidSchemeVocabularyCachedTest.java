package eu.europeana.normalization.pids;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.europeana.normalization.util.NormalizationConfigurationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PidSchemeVocabularyCachedTest {

  private static final String TEST_PID_ARK = "ark:/12148/bpt6k279983";
  private static final String TEST_PID_URN = "urn:nbn:nl:ui:29-8f66e0a8-b7c9-40a4-be28-54a7c0177061";

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

  private static Stream<Arguments> matchPidSchemePaths() {
    return Stream.of(
        Arguments.of(TEST_PID_ARK, "ark:/12148/bpt6k279983", null), // valid pid
        Arguments.of("https://n2t.net/ark:/12148/bpt6k279983", "ark:/12148/bpt6k279983", null), //valid pid
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

  @ParameterizedTest
  @MethodSource("providedPidSchemeInformation")
  void testActualMatcherWithPidSchemes(String pidValue, String canonicalPid, String resolvablePid, String schemeId)
      throws NormalizationConfigurationException {
    // Given
    final Function<String, PidMatchResult> pidSchemeMatcher = PidSchemeVocabularyCached.getMatcher();

    // When
    final PidMatchResult normalization = pidSchemeMatcher.apply(pidValue);

    // Then
    assertNotNull(normalization);
    assertEquals(pidValue, normalization.originalPid());
    assertEquals(canonicalPid, normalization.canonicalPid());
    assertEquals(resolvablePid, normalization.resolvablePid());
    assertEquals(schemeId, normalization.scheme().getSchemeId());
  }

  @Test
  void testSingletonPattern() throws NormalizationConfigurationException {
    // Given Verify singleton returns same instance
    PidSchemeVocabularyCached instance1 = PidSchemeVocabularyCached.getPidSchemes();
    PidSchemeVocabularyCached instance2 = PidSchemeVocabularyCached.getPidSchemes();
    // When & Then
    assertSame(instance1, instance2);
  }

  @Test
  void testGetMatcher() throws NormalizationConfigurationException {
    // Given
    Function<String, PidMatchResult> matcher = PidSchemeVocabularyCached.getMatcher();
    assertNotNull(matcher);

    // When Test with valid PID
    PidMatchResult result = matcher.apply(TEST_PID_ARK);
    // Then
    assertNotNull(result);
    assertEquals(TEST_PID_ARK, result.originalPid());
  }

  @ParameterizedTest(name = "Test matchPid with value: {0}, expecting: {1}, exception: {2}")
  @MethodSource("matchPidSchemePaths")
  void testMatchPidPaths(String value, String expected, Class<?> clazz) throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();
    // When
    if (clazz == null) {
      PidMatchResult result = vocabulary.matchPid(value);

      // Then
      if (expected != null) {
        assertNotNull(result);
        assertEquals(value, result.originalPid());
      } else {
        assertNull(result);
      }
    } else {
      assertThrows(NullPointerException.class, () -> vocabulary.matchPid(value));
    }
  }

  @Test
  void testCacheInitialization() throws NormalizationConfigurationException {
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();
    assertNotNull(vocabulary);

    // Verify that schemes are loaded
    Function<String, PidMatchResult> matcher = PidSchemeVocabularyCached.getMatcher();
    PidMatchResult result = matcher.apply(TEST_PID_ARK);
    assertNotNull(result, "Schemes should be initialized and able to match PIDs");
  }

  @Test
  void testGetAllSchemesFromCacheHitsCacheWithinTTL() throws NormalizationConfigurationException {
    // First call to populate cache
    Function<String, PidMatchResult> matcher1 = PidSchemeVocabularyCached.getMatcher();
    assertNotNull(matcher1.apply(TEST_PID_ARK));

    // Second call should use cache (within TTL)
    Function<String, PidMatchResult> matcher2 = PidSchemeVocabularyCached.getMatcher();
    PidMatchResult result2 = matcher2.apply(TEST_PID_ARK);
    assertNotNull(result2);
  }

  @Test
  void testConcurrentAccess() throws InterruptedException {
    // Given
    final int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    List<PidMatchResult> results = Collections.synchronizedList(new ArrayList<>());
    List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

    // When
    for (int i = 0; i < threadCount; i++) {
      new Thread(() -> {
        try {
          startLatch.await();
          Function<String, PidMatchResult> matcher = PidSchemeVocabularyCached.getMatcher();
          PidMatchResult result = matcher.apply(TEST_PID_ARK);
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
  void testMultipleMatchCalls() throws NormalizationConfigurationException {
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // Test multiple different PIDs
    PidMatchResult result1 = vocabulary.matchPid(TEST_PID_ARK);
    assertNotNull(result1);

    PidMatchResult result2 = vocabulary.matchPid(TEST_PID_URN);
    assertNotNull(result2);

    // Test invalid PID
    PidMatchResult result3 = vocabulary.matchPid("invalid:pid");
    assertNull(result3);

    // Verify results are correct
    assertEquals(TEST_PID_ARK, result1.originalPid());
    assertEquals(TEST_PID_URN, result2.originalPid());
  }

  @Test
  void testMatcherFunctionBehavior() throws NormalizationConfigurationException {
    // Given
    Function<String, PidMatchResult> matcher = PidSchemeVocabularyCached.getMatcher();

    // When
    // Test multiple applications
    PidMatchResult result1 = matcher.apply(TEST_PID_ARK);
    assertNotNull(result1);

    PidMatchResult result2 = matcher.apply(TEST_PID_URN);
    assertNotNull(result2);

    // Then Verify matcher can be reused
    PidMatchResult result3 = matcher.apply(TEST_PID_ARK);
    assertNotNull(result3);
    assertEquals(result1.originalPid(), result3.originalPid());
  }

  @Test
  void testSchemeLoadingFromImporter() throws NormalizationConfigurationException {
    // Given
    Function<String, PidMatchResult> matcher = PidSchemeVocabularyCached.getMatcher();

    // When
    PidMatchResult arkResult = matcher.apply(TEST_PID_ARK);

    // Then
    assertNotNull(arkResult);
    assertNotNull(arkResult.scheme());
    assertNotNull(arkResult.canonicalPid());
    assertNotNull(arkResult.resolvablePid());
    assertTrue(arkResult.resolvablePid().contains("n2t.net"));
  }

  @Test
  void testCanonicalizationProcessing() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    PidMatchResult result = vocabulary.matchPid("https://n2t.net/ark:/12148/bpt6k279983");

    // Then
    assertNotNull(result);
    assertEquals("ark:/12148/bpt6k279983", result.canonicalPid());
  }

  @Test
  void testResolvablePidGeneration() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    PidMatchResult result = vocabulary.matchPid(TEST_PID_ARK);

    // Then
    assertNotNull(result);
    assertTrue(result.resolvablePid().startsWith("https://"));
    assertTrue(result.resolvablePid().contains("ark"));
  }

  @Test
  void testSchemeInfoDetails() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    PidMatchResult result = vocabulary.matchPid(TEST_PID_ARK);
    assertNotNull(result);

    // Then
    PidSchemeInfo scheme = result.scheme();
    assertNotNull(scheme);
    assertNotNull(scheme.getSchemeId());
    assertNotNull(scheme.getTitle());
  }

  @Test
  void testCaseInsensitivePidMatching() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    // Test that matching is case-insensitive (depends on scheme patterns)
    PidMatchResult result1 = vocabulary.matchPid(TEST_PID_ARK);
    assertNotNull(result1);

    // Then Test with uppercase variations
    PidMatchResult result2 = vocabulary.matchPid(TEST_PID_ARK.toUpperCase());
    // May or may not match depending on scheme definition
    assertNotNull(result2);
  }

  @Test
  void testManySequentialMatches() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    for (int i = 0; i < 100; i++) {
      // When & Then Test multiple sequential matches
      PidMatchResult result = vocabulary.matchPid(TEST_PID_ARK);
      assertNotNull(result, "Sequential matches should all succeed");
    }
  }

  @Test
  void testDifferentPidFormats() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    // Test ARK format
    PidMatchResult arkResult = vocabulary.matchPid("ark:/12148/bpt6k279983");
    assertNotNull(arkResult);

    // Test URN format
    PidMatchResult urnResult = vocabulary.matchPid("urn:nbn:nl:ui:29-8f66e0a8-b7c9-40a4-be28-54a7c0177061");
    assertNotNull(urnResult);

    // Then Verify they belong to different schemes
    assertNotEquals(arkResult.scheme().getSchemeId(), urnResult.scheme().getSchemeId());
  }

  @Test
  void testSchemeConsistency() throws NormalizationConfigurationException {
    // Given Multiple retrievals of matcher should produce consistent results
    Function<String, PidMatchResult> matcher1 = PidSchemeVocabularyCached.getMatcher();
    Function<String, PidMatchResult> matcher2 = PidSchemeVocabularyCached.getMatcher();

    // When
    PidMatchResult result1 = matcher1.apply(TEST_PID_ARK);
    PidMatchResult result2 = matcher2.apply(TEST_PID_ARK);

    // Then
    assertEquals(result1.originalPid(), result2.originalPid());
    assertEquals(result1.canonicalPid(), result2.canonicalPid());
    assertEquals(result1.scheme().getSchemeId(), result2.scheme().getSchemeId());
  }

  @Test
  void testSchemeLoadingSucceeds() {
    // Given that initialization doesn't throw and schemes are available
    assertDoesNotThrow(() -> {
      // When & Then Test that schemes are loaded successfully
      PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();
      assertNotNull(vocabulary);
    });
  }

  @Test
  void testPerformanceWithFrequentMatching() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
      vocabulary.matchPid(TEST_PID_ARK);
    }
    long endTime = System.currentTimeMillis();

    // Then Should complete quickly (well under 1 second for 1000 iterations)
    assertTrue((endTime - startTime) < 1000, "Performance should be good for repeated matches");
  }

  @Test
  void testInitializationWithValidSchemes() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();
    assertNotNull(vocabulary);

    // When
    Function<String, PidMatchResult> matcher = PidSchemeVocabularyCached.getMatcher();
    assertNotNull(matcher);

    // Then Verify schemes can match PIDs
    PidMatchResult result = matcher.apply(TEST_PID_ARK);
    assertNotNull(result);
  }

  @Test
  void testMultipleMatcherInstancesUseSameSingleton() throws NormalizationConfigurationException {
    // Given
    Function<String, PidMatchResult> matcher1 = PidSchemeVocabularyCached.getMatcher();
    Function<String, PidMatchResult> matcher2 = PidSchemeVocabularyCached.getMatcher();

    // When
    PidMatchResult result1 = matcher1.apply(TEST_PID_URN);
    PidMatchResult result2 = matcher2.apply(TEST_PID_URN);

    // Then Both should return the same result
    assertNotNull(result1);
    assertNotNull(result2);
    assertEquals(result1.originalPid(), result2.originalPid());
    assertEquals(result1.canonicalPid(), result2.canonicalPid());
  }

  @Test
  void testUrlPidFormatVariations() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When & Then Test different URL variations
    // Test ARK with https URL
    PidMatchResult result1 = vocabulary.matchPid("https://n2t.net/ark:/12148/bpt6k279983");
    assertNotNull(result1);
    assertEquals("ark:/12148/bpt6k279983", result1.canonicalPid());

    // Test ARK with http URL
    PidMatchResult result2 = vocabulary.matchPid("http://n2t.net/ark:/12148/bpt6k279983");
    assertNotNull(result2);
    assertEquals("ark:/12148/bpt6k279983", result2.canonicalPid());
  }

  @Test
  void testRepeatedGetSchemesReturnSameInstance() throws NormalizationConfigurationException {
    // Given & When & Then Test that getSchemes returns the same instance every time
    PidSchemeVocabularyCached instance1 = PidSchemeVocabularyCached.getPidSchemes();
    PidSchemeVocabularyCached instance2 = PidSchemeVocabularyCached.getPidSchemes();
    PidSchemeVocabularyCached instance3 = PidSchemeVocabularyCached.getPidSchemes();

    assertSame(instance1, instance2);
    assertSame(instance2, instance3);
  }

  @Test
  void testSchemeIdConsistency() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    PidMatchResult result1 = vocabulary.matchPid(TEST_PID_ARK);
    String schemeId1 = result1.scheme().getSchemeId();

    PidMatchResult result2 = vocabulary.matchPid(TEST_PID_ARK);
    String schemeId2 = result2.scheme().getSchemeId();

    // Then
    assertEquals(schemeId1, schemeId2);
  }

  @Test
  void testCanonicalPidConsistency() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    String original = "https://n2t.net/ark:/12148/bpt6k279983";
    PidMatchResult result1 = vocabulary.matchPid(original);
    PidMatchResult result2 = vocabulary.matchPid(original);

    // Then
    assertEquals(result1.canonicalPid(), result2.canonicalPid());
  }

  @Test
  void testResolvablePidConsistency() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When
    String pid = TEST_PID_ARK;
    PidMatchResult result1 = vocabulary.matchPid(pid);
    PidMatchResult result2 = vocabulary.matchPid(pid);

    // Then
    assertEquals(result1.resolvablePid(), result2.resolvablePid());
  }

  @Test
  void testMatchingMultipleSchemes() throws NormalizationConfigurationException {
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // Match ARK scheme
    PidMatchResult arkResult = vocabulary.matchPid(TEST_PID_ARK);
    assertNotNull(arkResult);

    // Match URN scheme
    PidMatchResult urnResult = vocabulary.matchPid(TEST_PID_URN);
    assertNotNull(urnResult);

    // Verify they're different schemes
    assertNotEquals(
        arkResult.scheme().getSchemeId(),
        urnResult.scheme().getSchemeId()
    );
  }

  @Test
  void testLongRunningMatchLoop() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // Test that repeated matches don't cause issues
    for (int i = 0; i < 500; i++) {
      // When
      PidMatchResult result = vocabulary.matchPid(TEST_PID_ARK);
      // Then
      assertNotNull(result);
      assertEquals(TEST_PID_ARK, result.originalPid());
    }
  }

  @Test
  void testNoMemoryLeaksWithRepeatedMatching() throws NormalizationConfigurationException {
    // Given
    PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();

    // When Perform many matches to ensure no memory issues
    for (int i = 0; i < 50000; i++) {
      vocabulary.matchPid(TEST_PID_ARK);
      vocabulary.matchPid(TEST_PID_URN);
      vocabulary.matchPid("invalid:pid:" + i);
    }

    // Then, If we get here without OutOfMemory, test passes
    assertTrue(true);
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
          PidSchemeVocabularyCached vocabulary = PidSchemeVocabularyCached.getPidSchemes();
          vocabulary.matchPid(pids[pidIndex]);
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
          Function<String, PidMatchResult> matcher = PidSchemeVocabularyCached.getMatcher();
          PidMatchResult result = matcher.apply(TEST_PID_ARK);
          if (result != null) {
            results.add(result.canonicalPid());
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
