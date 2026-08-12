package eu.europeana.normalization.pids;

import eu.europeana.normalization.pids.RegexUtils.OptimalMatch;
import eu.europeana.normalization.pids.importer.PersistentIdentifierSchemeImporter;
import eu.europeana.normalization.pids.importer.PersistentIdentifierSchemeImporterFactory;
import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;
import eu.europeana.normalization.util.NormalizationConfigurationException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class loads a PID scheme vocabulary and provides functionality to match PIDs against it.
 * It implements a caching mechanism to avoid repeated remote imports, with a TTL of 24 hours.
 * The cache is thread-safe and uses a lock to prevent concurrent refreshes. If the import fails,
 * it falls back to the last known good cache if available.
 */
public final class PidSchemeVocabularyCached {

  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final long IMPORT_CACHE_TTL_HOUR = Duration.ofHours(24).toMillis();
  private static final String URI_SCHEME = "https://raw.githubusercontent.com/europeana/data-europeana-gateway/refs/heads/main/config/pid_directory.yaml";
  private static final int MAX_IMPORT_RETRIES = 5;
  private static final long RETRY_BACKOFF_MS = Duration.ofSeconds(1).toMillis();
  private final String sourceUri;
  private final ReentrantLock importCacheLock = new ReentrantLock();
  private final AtomicReference<List<PidScheme>> schemes = new AtomicReference<>(List.of());
  private volatile long lastSuccessfulImportTime = 0;

  /**
   * Instantiates new Pid scheme vocabulary cached.
   *
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  private PidSchemeVocabularyCached() throws NormalizationConfigurationException {
    this(URI_SCHEME);
  }

  /**
   * Instantiates new Pid scheme vocabulary cached.
   *
   * @param sourceUri the source uri
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
   private PidSchemeVocabularyCached(String sourceUri) throws NormalizationConfigurationException {
    if (sourceUri == null || sourceUri.isBlank()) {
      throw new IllegalArgumentException("sourceUri must not be blank");
    }
    this.sourceUri = sourceUri;
    // Pre-populate cache on initialization
    try {
      importPidSchemesWithRetry();
      LOGGER.info("PID scheme vocabulary initialized successfully");
    } catch (NormalizationConfigurationException e) {
      throw new NormalizationConfigurationException("Failed to initialize PID scheme vocabulary during construction", e);
    }
  }

  /**
   * Gets instance.
   *
   * @return the instance
   */
  public static PidSchemeVocabularyCached getInstance() {
    return PidSchemeVocabularyCacheHelper.INSTANCE;
  }

  /**
   * Tries to find a match in any of the schemes. If multiple patterns are matched,
   * we try to find the one that matches as early in the input as possible. If there is still
   * a tie, we try to find the longest match.
   *
   * @param input The input string from which to extract PIDs. Cannot be <code>null</code>.
   * @return A match result, or <code>null</code> if no match could be found.
   */
  private PidSingleMatchResult findBestMatch(String input)
      throws NormalizationConfigurationException {
    final OptimalMatch<PidSingleMatchResult> bestMatch = new OptimalMatch<>(
        PidSingleMatchResult::getMatchedSegment);
    getAllSchemesFromCache().forEach(scheme -> bestMatch.submitAlternative(scheme.find(input)));
    return bestMatch.getCurrentOptimum();
  }

  /**
   * Attempt to find PIDs in the given literal. Note: only part of the literal needs to
   * match a scheme.
   *
   * @param input The literal that may contain PIDs. Cannot be <code>null</code>.
   * @return The result of the search. If <code>null</code>, no PID scheme was found to match.
   */
  public PidMultipleMatchResult findPids(String input) {

    // Get all PIDs found in the input. Try all schemes repeatedly until no matches are found.
    String remainingInput = input;
    final List<PidSingleMatchResult> results = new ArrayList<>();
    try {
      while (true) {
        final PidSingleMatchResult bestMatch = findBestMatch(remainingInput);
        if (bestMatch != null) {
          results.add(bestMatch);
          remainingInput = remainingInput.substring(bestMatch.end());
        } else {
          break;
        }
      }
    } catch (NormalizationConfigurationException e) {
      LOGGER.error("Failed to match PID against PID scheme vocabulary", e);
    }

    // Compile the result.
    return PidMultipleMatchResult.forResults(results);
  }

  /**
   * Attempt to match a PID candidate against the vocabulary. Note: the entire literal needs to
   * match a scheme.
   *
   * @param pidCandidate The PID candidate to match. Cannot be <code>null</code>.
   * @return The result of the matching. If <code>null</code>, no PID scheme was found to match.
   */
  public PidSingleMatchResult matchPid(String pidCandidate) {
    try {
      for (PidScheme pidScheme : getAllSchemesFromCache()) {
        final PidSingleMatchResult pidMatchResult = pidScheme.match(pidCandidate);
        if (pidMatchResult != null) {
          return pidMatchResult;
        }
      }
    } catch (NormalizationConfigurationException e) {
      LOGGER.error("Failed to match PID against PID scheme vocabulary", e);
    }
    return null;
  }

  /**
   * Gets all schemes from the cache.
   * 1. Fast path: cache is still valid, return it directly.
   * 2. Slow path: the cache needs to refresh, use lock to prevent concurrent imports.
   *
   * @return the all schemes from cache
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  private List<PidScheme> getAllSchemesFromCache() throws NormalizationConfigurationException {
    // 1. Fast path: cache is still valid, return it directly
    if (!isCacheValid()) {
      // 2. Slow path: the cache needs to refresh, use lock to prevent concurrent imports
      refreshCacheIfNeeded();
    }
    return schemes.get();
  }

  /**
   * Is cache valid boolean.
   *
   * @return the boolean
   */
  private boolean isCacheValid() {
    return !schemes.get().isEmpty() && (System.currentTimeMillis() - lastSuccessfulImportTime) <= IMPORT_CACHE_TTL_HOUR;
  }

  /**
   * Refresh the cache if needed.
   *
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  private void refreshCacheIfNeeded() throws NormalizationConfigurationException {
    if (!importCacheLock.tryLock()) {
      LOGGER.debug("Another thread is refreshing the PID scheme cache");
      return;
    }
    try {
      if (isCacheValid()) {
        return;
      }
      importPidSchemesWithRetry();
    } finally {
      importCacheLock.unlock();
    }
  }

  /**
   * Attempts to import PID schemes with exponential backoff retry logic.
   * Provides better resilience against failures.
   *
   * @throws NormalizationConfigurationException if all retries fail
   */
  private void importPidSchemesWithRetry() throws NormalizationConfigurationException {
    NormalizationConfigurationException lastException = null;
    LOGGER.info("Importing PID schemes");
    int attempt = 1;
    boolean importSuccessful = false;
    while (attempt <= MAX_IMPORT_RETRIES && !importSuccessful) {
      try {
        LOGGER.debug("Attempting to import PID schemes (attempt {}/{})", attempt, MAX_IMPORT_RETRIES);
        schemes.set(List.copyOf(importPidSchemes()));
        importSuccessful = true;
      } catch (NormalizationConfigurationException exception) {
        lastException = exception;
        if (attempt < MAX_IMPORT_RETRIES) {
          long exponentialBackoffInMs = RETRY_BACKOFF_MS * (1L << (attempt - 1));
          LOGGER.warn("PID scheme import failed (attempt {}), retrying in {}ms: {}",
              attempt, exponentialBackoffInMs, exception.getMessage());
          try {
            Thread.sleep(exponentialBackoffInMs);
          } catch (InterruptedException interruptedException) {
            LOGGER.error("PID scheme import interrupted ", interruptedException);
            Thread.currentThread().interrupt();
          }
        } else {
          LOGGER.error("PID scheme import failed after {} attempts", MAX_IMPORT_RETRIES, exception);
        }
      }
      attempt++;
    }

    if (!importSuccessful) {
      LOGGER.warn("All import attempts failed, falling back to stale cache (age: {} seconds)",
          Duration.ofMillis(System.currentTimeMillis() - lastSuccessfulImportTime).toSeconds());
      if (schemes.get().isEmpty()) {
        throw new NormalizationConfigurationException("Could not import PID schemes after " + MAX_IMPORT_RETRIES + " attempts",
            lastException);
      }
    }
  }

  /**
   * Imports PID schemes from a remote source with proper error handling.
   * Single import operation without retry (called by importPidSchemesWithRetry).
   *
   * @return List of imported schemes
   * @throws NormalizationConfigurationException if import fails
   */
  private List<PidScheme> importPidSchemes() throws NormalizationConfigurationException {
    try {
      URI uri = new URI(sourceUri);
      final URL fileUrl = uri.toURL();
      final PersistentIdentifierSchemeImporter importer =
          new PersistentIdentifierSchemeImporterFactory()
              .createImporter(fileUrl);
      if (importer == null) {
        throw new NormalizationConfigurationException("Could not create importer for PID schemes URI: " + sourceUri, null);
      }

      final List<PidScheme> result = importer.importPidSchemes();

      if (result.isEmpty()) {
        throw new NormalizationConfigurationException("No PID schemes were successfully imported", null);
      }
      LOGGER.info("Successfully imported {} PID schemes", result.size());
      lastSuccessfulImportTime = System.currentTimeMillis();
      return result;

    } catch (URISyntaxException | MalformedURLException exception) {
      throw new NormalizationConfigurationException("Could not parse PID schemes URI: " + sourceUri, exception);
    } catch (PidSchemeImportException e) {
      throw new NormalizationConfigurationException("Could not import PID schemes from remote source", e);
    }
  }

  /**
   * The type Pid scheme vocabulary cache helper.
   */
  private static class PidSchemeVocabularyCacheHelper {

    private static final PidSchemeVocabularyCached INSTANCE;

    static {
      try {
        INSTANCE = new PidSchemeVocabularyCached();
      } catch (NormalizationConfigurationException e) {
        LOGGER.error("Failed to initialize PidSchemeVocabularyCached", e);
        throw new IllegalStateException("Initialization of PidSchemeVocabularyCached failed.", e);
      }
    }
  }
}
