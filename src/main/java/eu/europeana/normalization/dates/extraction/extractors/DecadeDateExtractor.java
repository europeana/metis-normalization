package eu.europeana.normalization.dates.extraction.extractors;

import static eu.europeana.normalization.dates.YearPrecision.DECADE;

import eu.europeana.normalization.dates.DateNormalizationExtractorMatchId;
import eu.europeana.normalization.dates.DateNormalizationResult;
import eu.europeana.normalization.dates.edtf.InstantEdtfDate;
import eu.europeana.normalization.dates.edtf.InstantEdtfDateBuilder;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extractor that matches a decade of the format YYY[ux].
 * <p>The decade may start and/or end with a question mark indicating uncertainty. Dates such as '198-' are not supported because
 * they may indicate a decade or a time period with an open end.</p>
 * <p>
 * Examples:
 *   <ul>
 *     <li>180u</li>
 *     <li>180x</li>
 *     <li>?180u</li>
 *     <li>?180x</li>
 *     <li>180??</li>
 *     <li>180x?</li>
 *   </ul>
 * </p>
 * <p>
 * A decade represented as YYYu or YYYx. For example, '198u', '198x' Dates such as '198-' are not supported because they may
 * indicate a decade or a time period with an open end
 */
public class DecadeDateExtractor extends AbstractDateExtractor {

  private static final Pattern decadePattern = Pattern.compile(
      OPTIONAL_QUESTION_MARK_REGEX + "(\\d{3})(?:[XU]" + OPTIONAL_QUESTION_MARK_REGEX + "|\\?\\?)", Pattern.CASE_INSENSITIVE);

  @Override
  public DateNormalizationResult extract(String inputValue, boolean allowDayMonthSwap) throws DateExtractionException {
    DateNormalizationResult dateNormalizationResult = DateNormalizationResult.getNoMatchResult(inputValue);
    final Matcher matcher = decadePattern.matcher(inputValue);
    if (matcher.matches()) {
      final InstantEdtfDate datePart = new InstantEdtfDateBuilder(Integer.parseInt(matcher.group(1)))
          .withYearPrecision(DECADE)
          .withDateQualification(getQualification(inputValue))
          .withAllowDayMonthSwap(allowDayMonthSwap)
          .build();
      dateNormalizationResult = new DateNormalizationResult(DateNormalizationExtractorMatchId.DECADE, inputValue, datePart);
    }
    return dateNormalizationResult;
  }
}
