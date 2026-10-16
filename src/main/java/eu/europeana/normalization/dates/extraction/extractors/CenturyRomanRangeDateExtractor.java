package eu.europeana.normalization.dates.extraction.extractors;

import eu.europeana.normalization.dates.DateNormalizationExtractorMatchId;
import eu.europeana.normalization.dates.DateNormalizationResult;
import eu.europeana.normalization.dates.DateNormalizationResultStatus;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import eu.europeana.normalization.dates.extraction.DefaultDatesSeparator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Extractor for Roman century ranges.
 * <p>We reuse the already existent {@link CenturyRomanDateExtractor} code for the boundaries.</p>
 */
public class CenturyRomanRangeDateExtractor extends AbstractRangeDateExtractor<DefaultDatesSeparator> {

  private final CenturyRomanDateExtractor romanCenturyDateExtractor;

  /**
   * Creates a range extractor using the system clock and default time zone.
   */
  public CenturyRomanRangeDateExtractor() {
    this(Clock.systemDefaultZone());
  }

  /**
   * Creates a range extractor whose endpoints use the supplied clock.
   *
   * @param clock the clock used to validate both century endpoints
   */
  public CenturyRomanRangeDateExtractor(Clock clock) {
    romanCenturyDateExtractor = new CenturyRomanDateExtractor(clock);
  }

  @Override
  public DateNormalizationResultRangePair extractDateNormalizationResult(String startString, String endString,
      DefaultDatesSeparator rangeDateDelimiters,
      boolean allowDayMonthSwap) throws DateExtractionException {
    return new DateNormalizationResultRangePair(
        romanCenturyDateExtractor.extract(startString, allowDayMonthSwap),
        romanCenturyDateExtractor.extract(endString, allowDayMonthSwap));
  }

  @Override
  public List<DefaultDatesSeparator> getRangeDateQualifiers() {
    return new ArrayList<>(EnumSet.of(DefaultDatesSeparator.DASH_DELIMITER));
  }

  @Override
  public boolean isRangeMatchSuccess(DefaultDatesSeparator rangeDateDelimiters, DateNormalizationResult startDateResult,
      DateNormalizationResult endDateResult) {
    return startDateResult.getDateNormalizationResultStatus() == DateNormalizationResultStatus.MATCHED
        && endDateResult.getDateNormalizationResultStatus() == DateNormalizationResultStatus.MATCHED;
  }

  @Override
  public DateNormalizationExtractorMatchId getDateNormalizationExtractorId(DateNormalizationResult startDateResult,
      DateNormalizationResult endDateResult) {
    return DateNormalizationExtractorMatchId.CENTURY_RANGE_ROMAN;
  }
}
