package eu.europeana.normalization.dates.extraction.extractors;

import static eu.europeana.normalization.dates.YearPrecision.CENTURY;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;

import eu.europeana.normalization.dates.DateNormalizationExtractorMatchId;
import eu.europeana.normalization.dates.DateNormalizationResult;
import eu.europeana.normalization.dates.edtf.InstantEdtfDate;
import eu.europeana.normalization.dates.edtf.InstantEdtfDateBuilder;
import eu.europeana.normalization.dates.extraction.CenturyDateValidator;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import java.time.Clock;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Extractor that matches centuries expressed using decimal numerals.
 * <p>Recognizes positive numbered centuries with optional ordinal suffixes, and two-digit year prefixes
 * from 10 to 99 followed by two dots. Numbered centuries must have started and fit the supported date representation. These forms
 * use different conversions: a numbered century is reduced by one, while a year prefix is used directly.</p>
 * <ul>
 *   <li>Value = {@code 18..} | Outcome = {@code 18XX}</li>
 *   <li>Value = {@code 1st century} | Outcome = {@code 00XX}</li>
 *   <li>Value = {@code 21st century} | Outcome = {@code 20XX}</li>
 *   <li>Value = {@code 21..} | Year prefix = {@code 21XX}</li>
 * </ul>
 */
public class CenturyNumericDateExtractor extends AbstractDateExtractor {

  private static final String YEAR_PREFIX_ENDING_DOTS_REGEX = "([1-9]\\d)\\.{2}";
  private static final String NUMBERED_CENTURY_REGEX = "([1-9]\\d*|0)(st|nd|rd|th)?\\scentury";

  private final Clock clock;

  @Getter
  @AllArgsConstructor
  private enum CenturyNumericDatePattern {
    PATTERN_YYYY(compile(OPTIONAL_QUESTION_MARK_REGEX + YEAR_PREFIX_ENDING_DOTS_REGEX + OPTIONAL_QUESTION_MARK_REGEX,
        CASE_INSENSITIVE), DateNormalizationExtractorMatchId.CENTURY_NUMERIC),
    PATTERN_ENGLISH(compile(OPTIONAL_QUESTION_MARK_REGEX + NUMBERED_CENTURY_REGEX + OPTIONAL_QUESTION_MARK_REGEX,
        CASE_INSENSITIVE), DateNormalizationExtractorMatchId.CENTURY_NUMERIC);

    private final Pattern pattern;
    private final DateNormalizationExtractorMatchId dateNormalizationExtractorMatchId;
  }

  /**
   * Creates an extractor using the system clock and default time zone.
   */
  public CenturyNumericDateExtractor() {
    this(Clock.systemDefaultZone());
  }

  /**
   * Creates an extractor using the supplied clock for temporal validation.
   *
   * @param clock the clock used by both century validation and the date builder
   */
  public CenturyNumericDateExtractor(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public DateNormalizationResult extract(String inputValue, boolean allowDayMonthSwap) throws DateExtractionException {
    DateNormalizationResult dateNormalizationResult = DateNormalizationResult.getNoMatchResult(inputValue);
    for (CenturyNumericDatePattern centuryNumericDatePattern : CenturyNumericDatePattern.values()) {
      final Matcher matcher = centuryNumericDatePattern.getPattern().matcher(inputValue);
      if (matcher.matches()) {
        final String century = matcher.group(1);
        final int numericValue;
        try {
          numericValue = Integer.parseInt(century);
        } catch (NumberFormatException e) {
          throw new DateExtractionException("Century number is too large", e);
        }
        if (centuryNumericDatePattern == CenturyNumericDatePattern.PATTERN_ENGLISH) {
          validateOrdinalSuffix(century, matcher.group(2));
          CenturyDateValidator.validate(numericValue, clock);
        }
        final int yearPrefix = (centuryNumericDatePattern == CenturyNumericDatePattern.PATTERN_ENGLISH)
            ? (numericValue - 1) : numericValue;
        InstantEdtfDateBuilder instantEdtfDateBuilder = new InstantEdtfDateBuilder(yearPrefix)
            .withYearPrecision(CENTURY).withClock(clock);
        InstantEdtfDate instantEdtfDate = instantEdtfDateBuilder.withDateQualification(getQualification(inputValue))
                                                                .withAllowDayMonthSwap(allowDayMonthSwap).build();
        dateNormalizationResult =
            new DateNormalizationResult(centuryNumericDatePattern.getDateNormalizationExtractorMatchId(), inputValue,
                instantEdtfDate);
        break;
      }
    }
    return dateNormalizationResult;
  }

  private static void validateOrdinalSuffix(String century, String suffix) throws DateExtractionException {
    if (suffix == null) {
      return;
    }
    final String expectedSuffix;
    if (century.endsWith("11") || century.endsWith("12") || century.endsWith("13")) {
      expectedSuffix = "th";
    } else {
      expectedSuffix = switch (century.charAt(century.length() - 1)) {
        case '1' -> "st";
        case '2' -> "nd";
        case '3' -> "rd";
        default -> "th";
      };
    }
    if (!expectedSuffix.equalsIgnoreCase(suffix)) {
      throw new DateExtractionException("Invalid ordinal suffix for century number");
    }
  }
}
