package eu.europeana.normalization.dates.edtf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.of;

import eu.europeana.normalization.dates.YearPrecision;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
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

  @ParameterizedTest
  @MethodSource
  void infersLongYearsWithoutRelaxingOrdinaryStrictDates(int year, boolean allowDayMonthSwap, String expected)
      throws DateExtractionException {
    final InstantEdtfDateBuilder builder = new InstantEdtfDateBuilder(year)
        .withAllowDayMonthSwap(allowDayMonthSwap)
        .withClock(Clock.fixed(Instant.parse("2050-06-15T00:00:00Z"), ZoneOffset.UTC));
    if (expected == null) {
      assertThrows(DateExtractionException.class, builder::build);
    } else {
      assertEquals(expected, builder.build().toString());
    }
  }

  private static Stream<Arguments> infersLongYearsWithoutRelaxingOrdinaryStrictDates() {
    return Stream.of(
        of(-9999, true, "-9999"),
        of(-9999, false, null),
        of(-10000, true, "Y-10000"),
        of(-10000, false, "Y-10000"),
        of(-999999999, true, "Y-999999999"),
        of(-999999999, false, "Y-999999999"),
        of(10000, true, null),
        of(10000, false, null)
    );
  }

  @ParameterizedTest
  @MethodSource
  void rejectsYearsOutsideJavaTimeRangeWithoutOverflow(int year, YearPrecision precision) {
    final InstantEdtfDateBuilder builder = new InstantEdtfDateBuilder(year).withYearPrecision(precision);
    assertThrows(DateExtractionException.class, builder::build);
  }

  private static Stream<Arguments> rejectsYearsOutsideJavaTimeRangeWithoutOverflow() {
    return Stream.of(
        of(-1000000000, YearPrecision.YEAR),
        of(Integer.MIN_VALUE, YearPrecision.YEAR),
        of(-429496730, YearPrecision.DECADE),
        of(Integer.MIN_VALUE, YearPrecision.CENTURY)
    );
  }

  @Test
  void infersLongYearsFromTemporalAccessorAndRebuildsTheirBounds() throws DateExtractionException {
    final InstantEdtfDate date = new InstantEdtfDateBuilder(Year.of(-10000)).withAllowDayMonthSwap(false).build();
    assertEquals("Y-10000", date.toString());
    assertEquals("Y-10000", date.getFirstDay().toString());
    assertEquals("Y-10000", date.getLastDay().toString());
  }

  @ParameterizedTest
  @MethodSource
  void validatesCalendarDatesWhenBuildingFutureBoundaries(int year, int month, int day, boolean valid) {
    final InstantEdtfDateBuilder builder = new InstantEdtfDateBuilder(year).withMonth(month).withDay(day)
        .withAllowDayMonthSwap(false)
        .withClock(Clock.fixed(Instant.parse("2050-06-15T00:00:00Z"), ZoneOffset.UTC));
    if (valid) {
      assertDoesNotThrow(builder::buildBoundary);
    } else {
      assertThrows(DateExtractionException.class, builder::buildBoundary);
    }
  }

  private static Stream<Arguments> validatesCalendarDatesWhenBuildingFutureBoundaries() {
    return Stream.of(
        of(2050, 6, 16, true),
        of(2052, 2, 29, true),
        of(2050, 2, 29, false),
        of(2050, 4, 31, false),
        of(2050, 13, 1, false)
    );
  }
}
