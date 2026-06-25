package eu.europeana.normalization.pids;

import eu.europeana.normalization.pids.model.PersistentIdentifierCorrection;
import eu.europeana.normalization.util.NormalizationConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

/**
 * This class represents the vocabulary of PID corrections. It has functionality for correcting a
 * value using the various correction schemes in this vocabulary.
 * TODO JV this should become a cached object like PidSchemeVocabularyCached.
 */
public final class PidCorrectionVocabulary {

  private static final String VOCABULARY_FILE = "https://raw.githubusercontent.com/europeana/data-europeana-gateway/refs/heads/main/public/scheme/pid/normalization.yml";

  private static PidCorrectionVocabulary instance;

  private final List<PidCorrection> pidCorrections;

  /**
   * Constructor
   */
  private PidCorrectionVocabulary() throws NormalizationConfigurationException {
    try {
      final URL fileUrl = new URI(VOCABULARY_FILE).toURL();
      try (final InputStream input = fileUrl.openStream()) {
        final String data = IOUtils.toString(input, StandardCharsets.UTF_8);
        final PersistentIdentifierCorrection[] pidCorrectionList = new ObjectMapper(
            new YAMLFactory()).readValue(data, PersistentIdentifierCorrection[].class);
        pidCorrections = Stream.of(pidCorrectionList)
            .map(correction -> new PidCorrection(correction.getMatch(), false,
                correction.getReplace()))
            .collect(Collectors.toList());
      }
    } catch (URISyntaxException | IOException exception) {
      throw new NormalizationConfigurationException("Could not parse PID schemes URI: " +
          VOCABULARY_FILE, exception);
    }
  }

  public static synchronized PidCorrectionVocabulary getInstance()
      throws NormalizationConfigurationException {
    if (instance == null) {
      instance = new PidCorrectionVocabulary();
    }
    return instance;
  }

  /**
   * Attempt to correct a PID candidate value according this vocabulary of correction schemes. We
   * do repeated correction attempts until no correction scheme has any correction to make.
   *
   * @param pidCandidate The value to correct.
   * @return The corrected value.
   */
  public String attemptCorrection(String pidCandidate) {

    // The current version keeps track of the latest changes. The previous version is the version
    // at the beginning of the round (i.e., the iteration through all corrections).
    String currentVersion = pidCandidate;
    String previousVersion;

    // Apply all corrections until we have a round (i.e., iteration) without any changes.
    do {
      previousVersion = currentVersion;
      for (PidCorrection correction : pidCorrections) {
        currentVersion = correction.attemptCorrection(currentVersion);
      }
    } while (!currentVersion.equals(previousVersion));

    // Done.
    return currentVersion;
  }
}
