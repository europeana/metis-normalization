package eu.europeana.normalization.normalizers;

import eu.europeana.normalization.model.NormalizationReport;
import eu.europeana.normalization.model.NormalizeActionResult;
import eu.europeana.normalization.model.RecordWrapper;
import eu.europeana.normalization.util.NormalizationException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents a normalizer with concatenated normalizer subtasks. This normalizer accepts
 * a list of other normalizers and executes them in the order given, providing the result of the
 * first as input to the second.
 */
public class ChainedNormalizer implements RecordNormalizeAction {

  private final List<RecordNormalizeAction> normalizations;

  /**
   * Constructor.
   * 
   * @param normalizations The normalizer subtasks.
   */
  public ChainedNormalizer(RecordNormalizeAction... normalizations) {
    this.normalizations = Arrays.stream(normalizations).collect(Collectors.toList());
  }

  @Override
  public NormalizeActionResult normalize(RecordWrapper record) throws NormalizationException {
    final NormalizationReport report = new NormalizationReport();
    RecordWrapper currentRecord = record;
    for (RecordNormalizeAction action : normalizations) {
      final NormalizeActionResult actionResult = action.normalize(currentRecord);
      currentRecord = actionResult.record();
      report.mergeWith(actionResult.report());
    }
    return new NormalizeActionResult(currentRecord, report);
  }
}
