package eu.europeana.normalization.dates.extraction.extractors;

import static eu.europeana.normalization.dates.YearPrecision.CENTURY;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;

import eu.europeana.normalization.dates.DateNormalizationExtractorMatchId;
import eu.europeana.normalization.dates.DateNormalizationResult;
import eu.europeana.normalization.dates.edtf.InstantEdtfDateBuilder;
import eu.europeana.normalization.dates.extraction.CenturyDateValidator;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import eu.europeana.normalization.dates.extraction.RomanToNumber;
import java.time.Clock;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extractor that matches a century with Roman numerals
 * <p>Recognizes correctly formed Roman numerals. The numbered century must have started
 * and fit the supported date representation. The numerals may also be preceded by an abbreviation
 * of century, for example ‘s. XIX’.</p>
 * <p>Examples of some cases:
 * <ul>
 *   <li>
 *     Value = s. XX | Outcome = 19XX
 *     Value = s. XXI | Outcome = 20XX
 *   </li>
 * </ul>
 * </p>
 */
public class CenturyRomanDateExtractor extends AbstractDateExtractor {

  private static final String CENTURY_PREFIX = "(?:(?:s|sec|saec)\\s|(?:s|sec|saec)\\.\\s?)?";
  private static final Pattern ROMAN_NUMERAL_PATTERN = compile(
      "M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})", CASE_INSENSITIVE);
  private static final Pattern ROMAN_CENTURY_PATTERN = compile(
      OPTIONAL_QUESTION_MARK_REGEX + CENTURY_PREFIX + "([MDCLXVI]+)" + OPTIONAL_QUESTION_MARK_REGEX, CASE_INSENSITIVE);

  private final Clock clock;

  /**
   * Creates an extractor using the system clock and default time zone.
   */
  public CenturyRomanDateExtractor() {
    this(Clock.systemDefaultZone());
  }

  /**
   * Creates an extractor using the supplied clock for temporal validation.
   *
   * @param clock the clock used by both century validation and the date builder
   */
  public CenturyRomanDateExtractor(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public DateNormalizationResult extract(String inputValue, boolean allowDayMonthSwap) throws DateExtractionException {
    DateNormalizationResult dateNormalizationResult = DateNormalizationResult.getNoMatchResult(inputValue);
    final Matcher matcher = ROMAN_CENTURY_PATTERN.matcher(inputValue);
    if (matcher.matches() && ROMAN_NUMERAL_PATTERN.matcher(matcher.group(1)).matches()) {
      final int centuryNumber = RomanToNumber.romanToDecimal(matcher.group(1));
      CenturyDateValidator.validate(centuryNumber, clock);
      final int century = centuryNumber - 1;
      final InstantEdtfDateBuilder instantEdtfDateBuilder =
          new InstantEdtfDateBuilder(century).withYearPrecision(CENTURY).withClock(clock)
              .withDateQualification(getQualification(inputValue));
      dateNormalizationResult = new DateNormalizationResult(DateNormalizationExtractorMatchId.CENTURY_ROMAN,
          inputValue, instantEdtfDateBuilder.build());
    }
    return dateNormalizationResult;
  }
}
