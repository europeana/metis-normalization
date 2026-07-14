package eu.europeana.normalization.normalizers;

import eu.europeana.normalization.model.ConfidenceLevel;
import eu.europeana.normalization.model.NormalizationReport;
import java.util.stream.IntStream;

/**
 * This is a subclass of {@link NormalizationReport} for internal use within the normalizers to provide access to the
 * {@link #increment(String, ConfidenceLevel)} method.
 *
 * @author jochen
 */
class InternalNormalizationReport extends NormalizationReport {

  @Override
  public void increment(String operation, ConfidenceLevel confidence) {
    super.increment(operation, confidence);
  }

  /**
   * Apply the increment multiple times by calling {@link #increment(String, ConfidenceLevel)} the
   * provided number of times.
   *
   * @param operation  The operation.
   * @param confidence The confidence of this operation.
   * @param count      The number of increments to apply.
   */
  public void multipleIncrement(String operation, ConfidenceLevel confidence, int count) {
    IntStream.range(0, count).forEach(i -> increment(operation, confidence));
  }
}

