package eu.europeana.normalization.dates.extraction.extractors;

import static eu.europeana.normalization.dates.DateNormalizationExtractorMatchId.CENTURY_NUMERIC;
import static org.junit.jupiter.params.provider.Arguments.of;

import eu.europeana.normalization.dates.DateNormalizationExtractorMatchId;
import eu.europeana.normalization.dates.DateNormalizationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CenturyNumericDateExtractorTest implements DateExtractorTest {

  private static final CenturyNumericDateExtractor CENTURY_DATE_EXTRACTOR = new CenturyNumericDateExtractor(
      Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC));

  void assertExtract(String input, String expected, DateNormalizationExtractorMatchId dateNormalizationExtractorMatchId) {
    final DateNormalizationResult dateNormalizationResult = CENTURY_DATE_EXTRACTOR.extractDateProperty(input);
    assertDateNormalizationResult(dateNormalizationResult, expected, dateNormalizationExtractorMatchId);
  }

  @ParameterizedTest
  @MethodSource
  void extractNumeric(String input, String expected, DateNormalizationExtractorMatchId dateNormalizationExtractorMatchId) {
    assertExtract(input, expected, dateNormalizationExtractorMatchId);
  }

  private static Stream<Arguments> extractNumeric() {
    return Stream.of(
        //PATTERN_YYYY
        of("18..", "18XX", CENTURY_NUMERIC),
        of("  18..  ", "18XX", CENTURY_NUMERIC),
        of("?18..", "18XX?", CENTURY_NUMERIC),
        of("18..?", "18XX?", CENTURY_NUMERIC),
        of("?18..?", "18XX?", CENTURY_NUMERIC),
        of("192?", null, null, null), //Too many digits
        of("1..", null, null, null), //Too few digits

        //PATTERN_ENGLISH
        of("1st century", "00XX", CENTURY_NUMERIC),
        of("2nd century", "01XX", CENTURY_NUMERIC),
        of("3rd century", "02XX", CENTURY_NUMERIC),
        of("11th century", "10XX", CENTURY_NUMERIC),
        of("19 century", "18XX", CENTURY_NUMERIC),
        of("19TH century", "18XX", CENTURY_NUMERIC),
        of("21st century", "20XX", CENTURY_NUMERIC),
        of("  11th century  ", "10XX", CENTURY_NUMERIC),
        of("?11th century", "10XX?", CENTURY_NUMERIC),
        of("11th century?", "10XX?", CENTURY_NUMERIC),
        of("?11th century?", "10XX?", CENTURY_NUMERIC),
        of("12th century BC", null, null, null), // not supported
        of("[10th century]", null, null, null), // not supported
        of("11thcentury", null, null, null), //Incorrect spacing numeric
        of("11st century", null, null, null), //Incorrect suffix
        of("12rd century", null, null, null), //Incorrect suffix
        of("13st century", null, null, null), //Incorrect suffix
        of("21th century", null, null, null), //Incorrect suffix
        of("0st century", null, null, null), //Out of range
        of("22nd century", null, null, null) //Future century
    );
  }

  @ParameterizedTest
  @MethodSource
  void extractAtDate(String date, String input, String expected) {
    final Clock clock = Clock.fixed(Instant.parse(date + "T00:00:00Z"), ZoneOffset.UTC);
    final DateNormalizationResult result = new CenturyNumericDateExtractor(clock).extractDateProperty(input);
    assertDateNormalizationResult(result, expected, CENTURY_NUMERIC);
  }

  private static Stream<Arguments> extractAtDate() {
    return Stream.of(
        of("2026-10-07", "21st century", "20XX"),
        of("2026-10-07", "22nd century", null),
        of("2100-12-31", "22nd century", null),
        of("2101-01-01", "22nd century", "21XX"),
        of("2101-01-01", "22 century", "21XX"),
        of("2101-01-01", "?22ND century?", "21XX?"),
        of("2101-01-01", "22th century", null),
        of("2201-01-01", "23rd century", "22XX"),
        of("2301-01-01", "24th century", "23XX"),
        of("3001-01-01", "31st century", "30XX"),
        of("3001-01-01", "31th century", null),
        of("2026-10-07", "0 century", null),
        of("2026-10-07", "0th century", null),
        of("2026-10-07", "01st century", null),
        of("2026-10-07", "2147483647th century", null),
        of("2026-10-07", "2147483648th century", null),
        of("2026-10-07", "999999999999999999999th century", null),
        of("2099-12-31", "21..", null),
        of("2100-01-01", "21..", "21XX"),
        of("2199-12-31", "22..", null),
        of("2200-01-01", "22..", "22XX"),
        of("9999-12-31", "99..", "99XX"),
        of("9999-12-31", "100..", null),
        of("9999-12-31", "09..", null),
        of("9900-12-31", "100th century", null),
        of("9901-01-01", "100th century", "99XX"),
        of("+10001-01-01", "101st century", null)
    );
  }

}