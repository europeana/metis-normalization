package eu.europeana.normalization.dates.extraction.extractors;

import static org.junit.jupiter.params.provider.Arguments.of;

import eu.europeana.normalization.dates.DateNormalizationExtractorMatchId;
import eu.europeana.normalization.dates.DateNormalizationResult;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LongNegativeYearDateExtractorTest implements DateExtractorTest {

  private static final LongNegativeYearDateExtractor LONG_NEGATIVE_YEAR_DATE_EXTRACTOR = new LongNegativeYearDateExtractor();

  @ParameterizedTest
  @MethodSource
  void extract(String input, String expected) {
    assertExtract(input, expected);
  }

  void assertExtract(String input, String expected) {
    final DateNormalizationResult dateNormalizationResult = LONG_NEGATIVE_YEAR_DATE_EXTRACTOR.extractDateProperty(input);
    assertDateNormalizationResult(dateNormalizationResult, expected, DateNormalizationExtractorMatchId.LONG_NEGATIVE_YEAR);
  }

  @ParameterizedTest
  @MethodSource
  void preservesLongYearsForGenericProperties(String input, String expected) {
    final DateNormalizationResult result = LONG_NEGATIVE_YEAR_DATE_EXTRACTOR.extractGenericProperty(input);
    assertDateNormalizationResult(result, expected, DateNormalizationExtractorMatchId.LONG_NEGATIVE_YEAR);
  }

  private static Stream<Arguments> preservesLongYearsForGenericProperties() {
    return Stream.of(
        of("-10000", "Y-10000"),
        of("-999999999", "Y-999999999"),
        of("-00010000", "Y-10000"),
        of("-9999", null),
        of("-09999", null),
        of("-00001", null),
        of("10000", null)
    );
  }

  private static Stream<Arguments> extract() {
    return Stream.of(
        of("-12345", "Y-12345"),
        of("-10000", "Y-10000"),
        of("-999999999", "Y-999999999"),
        of("-00010000", "Y-10000"),
        of("-09999", null),
        of("-00001", null),
        of("00000", null),
        of("-123456", "Y-123456"),
        of("-1234567", "Y-1234567"),
        of("-12345678", "Y-12345678"),
        of("-123456789", "Y-123456789"),

        //Future dates are not valid
        of("123456789", null),
        //Less digits
        of("-1234", null),
        //Greater digits
        of("-1234567890", null)
    );
  }
}