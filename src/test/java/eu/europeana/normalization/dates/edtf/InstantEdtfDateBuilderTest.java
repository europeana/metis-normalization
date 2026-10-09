package eu.europeana.normalization.dates.edtf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.of;

import eu.europeana.normalization.dates.extraction.DateExtractionException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InstantEdtfDateBuilderTest {

  @ParameterizedTest
  @MethodSource
  void validatesFutureDatesUsingClock(Integer year, Integer month, Integer day, boolean future) {
    final Clock clock = Clock.fixed(Instant.parse("2050-06-15T00:00:00Z"), ZoneOffset.UTC);
    final InstantEdtfDateBuilder builder = new InstantEdtfDateBuilder(year).withClock(clock);
    if (month != null) {
      builder.withMonth(month);
    }
    if (day != null) {
      builder.withDay(day);
    }
    if (future) {
      assertThrows(DateExtractionException.class, builder::build);
    } else {
      assertDoesNotThrow(builder::build);
    }
  }

  private static Stream<Arguments> validatesFutureDatesUsingClock() {
    return Stream.of(
        of(2049, null, null, false),
        of(2050, null, null, false),
        of(2051, null, null, true),
        of(2050, 5, null, false),
        of(2050, 6, null, false),
        of(2050, 7, null, true),
        of(2050, 6, 14, false),
        of(2050, 6, 15, false),
        of(2050, 6, 16, true)
    );
  }
}
