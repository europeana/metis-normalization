package eu.europeana.normalization.normalizers;

import eu.europeana.normalization.model.NormalizeActionResult;
import eu.europeana.normalization.model.RecordWrapper;
import eu.europeana.normalization.util.NormalizationException;

/**
 * An instance of this class performs a normalize action on an EDM document (represented as a DOM
 * tree) and provide feedback of their actions in a report.
 */
public interface RecordNormalizeAction extends NormalizeAction {

  /**
   * This method performs the normalize action.
   *
   * @param record The record to normalize. This wrapper should be considered outdated after
   *               this method is called, and the one in the return object should be used.
   * @return An object containing the normalized record and a report on the actions of this normalizer.
   * @throws NormalizationException If something goes wrong during normalization.
   */
  NormalizeActionResult normalize(RecordWrapper record) throws NormalizationException;

  /**
   * Default behavior: return the current instance.
   */
  @Override
  default RecordNormalizeAction getAsRecordNormalizer() {
    return this;
  }
}
