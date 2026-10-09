package eu.europeana.normalization.normalizers;

import eu.europeana.normalization.model.ConfidenceLevel;

/**
 * The normalized value including the confidence that the normalization is correct. This object is used within the normalization
 * functionality and not exposed to the calling code.
 */
record NormalizedValueWithConfidence(String normalizedValue, float confidence) {

  /**
   *
   * @return The confidence level that contains the confidence.
   */
  public ConfidenceLevel getConfidenceClass() {
    return ConfidenceLevel.getForConfidence(confidence);
  }
}
