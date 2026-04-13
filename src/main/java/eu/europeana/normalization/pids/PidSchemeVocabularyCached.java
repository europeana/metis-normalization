package eu.europeana.normalization.pids;

import eu.europeana.normalization.pids.importer.PersistentIdentifierSchemeImporter;
import eu.europeana.normalization.pids.importer.PersistentIdentifierSchemeImporterFactory;
import eu.europeana.normalization.pids.importer.exception.PidSchemeImportException;
import eu.europeana.normalization.pids.importer.model.PidSchemeLoadable;
import eu.europeana.normalization.util.NormalizationConfigurationException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
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
  private static final long MILLISECONDS_PER_SECOND = 1000L;
  private static final long IMPORT_CACHE_TTL_HOUR = 24L * 3600L * MILLISECONDS_PER_SECOND;
  private static final String URI_SCHEME = "https://raw.githubusercontent.com/europeana/data-europeana-gateway/refs/heads/main/config/pid_directory.yaml";
  private static final int MAX_IMPORT_RETRIES = 5;
  private static final long RETRY_BACKOFF_MS = 1000L;

  private static PidSchemeVocabularyCached instance;
  private final CopyOnWriteArrayList<PidScheme> schemes;
  private final ReentrantLock importCacheLock;
  private volatile long lastImportTime = 0;

  /**
   * Instantiates a new Pid scheme vocabulary cached.
   *
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  private PidSchemeVocabularyCached() throws NormalizationConfigurationException {
    schemes = new CopyOnWriteArrayList<>();
    importCacheLock = new ReentrantLock();
    // Pre-populate cache on initialization
    try {
      initializePidSchemes();
      LOGGER.info("PID scheme vocabulary initialized successfully");
    } catch (NormalizationConfigurationException e) {
      throw new NormalizationConfigurationException("Failed to initialize PID scheme vocabulary during construction", e);
    }
  }

  /**
   * This method provides access to the PID scheme vocabulary. Thread-safe singleton accessor.
   *
   * @return The PID scheme vocabulary.
   * @throws NormalizationConfigurationException If the vocabulary could not be loaded.
   */
  public static PidSchemeVocabularyCached getPidSchemes() throws NormalizationConfigurationException {
    synchronized (PidSchemeVocabularyCached.class) {
      if (instance == null) {
        instance = new PidSchemeVocabularyCached();
      }
      return instance;
    }
  }

  /**
   * Creates a matcher for PIDs that can match a PID against this vocabulary.
   *
   * @return A PID matcher.
   * @throws NormalizationConfigurationException If the vocabulary could not be loaded.
   */
  public static Function<String, PidMatchResult> getMatcher()
      throws NormalizationConfigurationException {
    return getPidSchemes()::matchPid;
  }

  /**
   * Attempt to match a PID against the vocabulary.
   *
   * @param pid The PID to match.
   * @return The result of the matching. If <code>null</code>, no PID scheme was found to match.
   */
  public PidMatchResult matchPid(String pid) {
    try {
      for (PidScheme pidScheme : getAllSchemesFromCache()) {
        PidMatchResult pidMatchResult = pidScheme.match(pid);
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
   * Initialize pid schemes.
   *
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  private void initializePidSchemes() throws NormalizationConfigurationException {
    schemes.addAll(importPidSchemesWithRetry());
  }

  /**
   * Gets all schemes from cache.
   * 1. Fast path: cache is still valid, return it directly.
   * 2. Slow path: cache needs to refresh, use lock to prevent concurrent imports.
   *
   * @return the all schemes from cache
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  private List<PidScheme> getAllSchemesFromCache() throws NormalizationConfigurationException {
    long currentTime = System.currentTimeMillis();

    // 1. Fast path: cache is still valid
    if (!schemes.isEmpty() && (currentTime - lastImportTime) <= IMPORT_CACHE_TTL_HOUR) {
      return Collections.unmodifiableList(schemes);
    }

    // 2. Slow path: cache needs to refresh, use lock to prevent concurrent imports
    try {
      importCacheLock.lock();
      currentTime = System.currentTimeMillis();
      if (!schemes.isEmpty() && (currentTime - lastImportTime) <= IMPORT_CACHE_TTL_HOUR) {
        return Collections.unmodifiableList(schemes);
      }

      LOGGER.info("Refreshing PID schemes import cache");
      List<PidScheme> imported = importPidSchemesWithRetry();
      schemes.clear();
      schemes.addAll(imported);
      lastImportTime = System.currentTimeMillis();
      return Collections.unmodifiableList(schemes);
    } finally {
      importCacheLock.unlock();
    }
  }

  /**
   * Attempts to import PID schemes with exponential backoff retry logic.
   * Provides better resilience against failures.
   *
   * @return List of imported schemes
   * @throws NormalizationConfigurationException if all retries fail
   */
  private List<PidScheme> importPidSchemesWithRetry() throws NormalizationConfigurationException {
    NormalizationConfigurationException lastException = null;

    for (int attempt = 1; attempt <= MAX_IMPORT_RETRIES; attempt++) {
      try {
        LOGGER.debug("Attempting to import PID schemes (attempt {}/{})", attempt, MAX_IMPORT_RETRIES);
        return importPidSchemes();
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
    }

    if (!schemes.isEmpty()) {
      lastImportTime = System.currentTimeMillis();
      LOGGER.warn("All import attempts failed, falling back to stale cache (age: {} seconds)",
          (System.currentTimeMillis() - lastImportTime) / MILLISECONDS_PER_SECOND);
      return List.copyOf(schemes);
    }

    throw new NormalizationConfigurationException("Could not import PID schemes after " + MAX_IMPORT_RETRIES + " attempts",
        lastException);
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
      URI uri = new URI(URI_SCHEME);
      final URL fileUrl = uri.toURL();
      final PersistentIdentifierSchemeImporterFactory factory = new PersistentIdentifierSchemeImporterFactory();
      final PersistentIdentifierSchemeImporter importer = factory.createImporter(fileUrl);

      final List<PidScheme> result = loadSchemesFromImporter(importer);

      if (result.isEmpty()) {
        throw new NormalizationConfigurationException("No PID schemes were successfully imported", null);
      }

      LOGGER.info("Successfully imported {} PID schemes", result.size());
      return result;

    } catch (URISyntaxException | MalformedURLException exception) {
      throw new NormalizationConfigurationException("Could not parse PID schemes URI: " + URI_SCHEME, exception);
    } catch (PidSchemeImportException e) {
      throw new NormalizationConfigurationException("Could not import PID schemes from remote source", e);
    }
  }

  private List<PidScheme> loadSchemesFromImporter(PersistentIdentifierSchemeImporter importer) throws PidSchemeImportException {
    final List<PidScheme> result = new ArrayList<>();
    final Iterable<PidSchemeLoadable> pidSchemesLoadable = importer.importPidSchemes();

    for (PidSchemeLoadable pidSchemeLoadable : pidSchemesLoadable) {
      if (pidSchemeLoadable == null) {
        LOGGER.warn("Skipping null PID scheme from importer");
        continue;
      }
      try {
        result.add(pidSchemeLoadable.load());
      } catch (PidSchemeImportException exception) {
        LOGGER.error("Failed to load individual PID scheme, continuing with others", exception);
      }
    }
    return Collections.unmodifiableList(result);
  }
}
