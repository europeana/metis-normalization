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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

  private List<PidScheme> schemes;
  private final ReentrantLock importCacheLock;
  private final String sourceUri;
  private volatile long lastSuccessfulImportTime = 0;

  /**
   * Instantiates new Pid scheme vocabulary cached.
   *
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  public PidSchemeVocabularyCached() throws NormalizationConfigurationException {
    this(URI_SCHEME);
  }

  /**
   * Instantiates new Pid scheme vocabulary cached.
   *
   * @param sourceUri the source uri
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  PidSchemeVocabularyCached(String sourceUri) throws NormalizationConfigurationException {
    if (sourceUri == null || sourceUri.isBlank()) {
      throw new IllegalArgumentException("sourceUri must not be blank");
    }
    this.sourceUri = sourceUri;
    schemes = new ArrayList<>();
    importCacheLock = new ReentrantLock();
    // Pre-populate cache on initialization
    try {
      refreshCache();
      LOGGER.info("PID scheme vocabulary initialized successfully");
    } catch (NormalizationConfigurationException e) {
      throw new NormalizationConfigurationException("Failed to initialize PID scheme vocabulary during construction", e);
    }
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
   * Gets all schemes from the cache.
   * 1. Fast path: cache is still valid, return it directly.
   * 2. Slow path: the cache needs to refresh, use lock to prevent concurrent imports.
   *
   * @return the all schemes from cache
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  private List<PidScheme> getAllSchemesFromCache() throws NormalizationConfigurationException {
    // 1. Fast path: cache is still valid
    if (isCacheValid()) {
      return Collections.unmodifiableList(schemes);
    }

    // 2. Slow path: the cache needs to refresh, use lock to prevent concurrent imports
    refreshCacheIfNeeded();
    return Collections.unmodifiableList(schemes);
  }

  /**
   * Is cache valid boolean.
   *
   * @return the boolean
   */
  private boolean isCacheValid() {
    return !schemes.isEmpty() && (System.currentTimeMillis() - lastSuccessfulImportTime) <= IMPORT_CACHE_TTL_HOUR;
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
      refreshCache();
    } finally {
      importCacheLock.unlock();
    }
  }

  /**
   * Refresh cache.
   *
   * @throws NormalizationConfigurationException the normalization configuration exception
   */
  private void refreshCache() throws NormalizationConfigurationException {
    LOGGER.info("Refreshing PID schemes import cache");
    schemes.clear();
    schemes.addAll(importPidSchemesWithRetry());
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
      LOGGER.warn("All import attempts failed, falling back to stale cache (age: {} seconds)",
          Duration.ofMillis(System.currentTimeMillis() - lastSuccessfulImportTime).toSeconds());
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
      URI uri = new URI(sourceUri);
      final URL fileUrl = uri.toURL();
      final PersistentIdentifierSchemeImporter importer =
          new PersistentIdentifierSchemeImporterFactory()
              .createImporter(fileUrl);
      if (importer == null) {
        throw new NormalizationConfigurationException("Could not create importer for PID schemes URI: " + sourceUri, null);
      }

      final List<PidScheme> result = loadSchemesFromImporter(importer);

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
   * Load schemes from the importer list.
   *
   * @param importer the importer
   * @return the list
   * @throws PidSchemeImportException the pid scheme import exception
   */
  private List<PidScheme> loadSchemesFromImporter(PersistentIdentifierSchemeImporter importer) throws PidSchemeImportException {
    final List<PidScheme> result = new ArrayList<>();
    for (PidSchemeLoadable pidSchemeLoadable : importer.importPidSchemes()) {
      if (pidSchemeLoadable == null) {
        LOGGER.warn("Skipping null PID scheme from importer");
        continue;
      }
      try {
        result.add(pidSchemeLoadable.load());
      } catch (PidSchemeImportException exception) {
        LOGGER.warn("Failed to load individual PID scheme skipping it, continuing with others", exception);
      }
    }
    return Collections.unmodifiableList(result);
  }
}
