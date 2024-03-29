package eu.europeana.normalization.dates.extraction.extractors;

import eu.europeana.normalization.dates.DateNormalizationExtractorMatchId;
import eu.europeana.normalization.dates.DateNormalizationResult;
import eu.europeana.normalization.dates.edtf.InstantEdtfDate;
import eu.europeana.normalization.dates.edtf.InstantEdtfDateBuilder;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A year before 1 AD with more than 4 digits. This pattern is typically used in archaeological contexts. The year may contain
 * between 5 and 9 digits. Aso includes the pattern for ranges of this kind of years.
 */
public class LongNegativeYearDateExtractor extends AbstractDateExtractor {

  private static final Pattern YEAR_PATTERN = Pattern.compile("(-?\\d{5,9})");

  @Override
  public DateNormalizationResult extract(String inputValue, boolean allowDayMonthSwap) throws DateExtractionException {
    DateNormalizationResult dateNormalizationResult = DateNormalizationResult.getNoMatchResult(inputValue);
    final Matcher matcher = YEAR_PATTERN.matcher(inputValue);
    if (matcher.matches()) {
      final int year = Integer.parseInt(matcher.group(1));
      final InstantEdtfDate instantEdtfDate =
          new InstantEdtfDateBuilder(year).withDateQualification(getQualification(inputValue))
                                          .withMoreThanFourDigitsYear()
                                          .withAllowDayMonthSwap(allowDayMonthSwap).build();
      dateNormalizationResult = new DateNormalizationResult(DateNormalizationExtractorMatchId.LONG_NEGATIVE_YEAR, inputValue,
          instantEdtfDate);
    }
    return dateNormalizationResult;
  }

}
