package eu.europeana.normalization.dates.extraction;

import static eu.europeana.normalization.dates.YearPrecision.CENTURY;
import static eu.europeana.normalization.dates.edtf.InstantEdtfDateBuilder.THRESHOLD_4_DIGITS_YEAR;

import java.time.Clock;
import java.time.Year;

/**
 * Validates numbered centuries before conversion to an EDTF year prefix.
 */
public final class CenturyDateValidator {

  private CenturyDateValidator() {
  }

  /**
   * Checks that a positive numbered century fits the supported year representation and has started.
   *
   * @param centuryNumber the numbered century, before conversion to an EDTF year prefix
   * @param clock the clock used to determine the current year
   * @throws DateExtractionException if the century is invalid, unsupported, or has not started
   */
  public static void validate(int centuryNumber, Clock clock) throws DateExtractionException {
    if (centuryNumber < 1) {
      throw new DateExtractionException("Century number must be positive");
    }
    // A numbered century starts one year after its stored EDTF base year.
    final long firstYear = (centuryNumber - 1L) * CENTURY.getDuration() + 1;
    if (firstYear > THRESHOLD_4_DIGITS_YEAR) {
      throw new DateExtractionException("Century exceeds the supported four-digit year representation");
    }
    if (firstYear > Year.now(clock).getValue()) {
      throw new DateExtractionException("Century has not started yet");
    }
  }
}
