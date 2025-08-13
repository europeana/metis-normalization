package eu.europeana.normalization.pids;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import eu.europeana.normalization.util.NormalizationConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;

/**
 * This class loads a PID scheme vocabulary and provides functionality to match PIDs against it.
 */
public final class PidSchemeVocabulary {

  private static PidSchemeVocabulary instance;

  private final Collection<PidScheme> schemes = new ArrayList<>();

  private PidSchemeVocabulary() throws NormalizationConfigurationException {
    final XmlMapper xmlMapper = new XmlMapper();
    try (final InputStream zipFile = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("pid_schemes.zip")) {
      if (zipFile == null) {
        throw new NormalizationConfigurationException("Could not find PID schemes zipfile.", null);
      }
      try (final ZipInputStream zipStream = new ZipInputStream(zipFile)) {
        ZipEntry entry = zipStream.getNextEntry();
        while (entry != null) {
          // Don't send zipStream to xmlMapper.readValue() as it will close the stream internally.
          final byte[] bytes = IOUtils.toByteArray(zipStream);
          xmlMapper.readValue(bytes, PersistentIdentifierSchemes.class).getSchemes().stream()
              .map(PidScheme::new).forEach(schemes::add);
          zipStream.closeEntry();
          entry = zipStream.getNextEntry();
        }
      }
    } catch (IOException e) {
      throw new NormalizationConfigurationException("Problem loading PID scheme vocabulary.", e);
    }
  }

  /**
   * This method provides access to the PID scheme vocabulary.
   *
   * @return The PID scheme vocabulary.
   * @throws NormalizationConfigurationException If the vocabulary could not be loaded.
   */
  public static PidSchemeVocabulary getPidSchemes() throws NormalizationConfigurationException {
    synchronized (PidSchemeVocabulary.class) {
      if (instance == null) {
        instance = new PidSchemeVocabulary();
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
  public Function<String, PidMatchResult> getMatcher() throws NormalizationConfigurationException {
    return getPidSchemes()::matchPid;
  }

  /**
   * Attempt to match a PID against the vocabulary.
   *
   * @param pid The PID to match.
   * @return The result of the matching. If <code>null</code>, no PID scheme was found to match.
   */
  public PidMatchResult matchPid(String pid) {
    return this.schemes.stream().map(scheme -> scheme.match(pid))
        .filter(Objects::nonNull).findFirst().orElse(null);
  }
}
