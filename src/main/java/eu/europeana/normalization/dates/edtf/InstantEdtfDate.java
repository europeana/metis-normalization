package eu.europeana.normalization.dates.edtf;

import static eu.europeana.normalization.dates.edtf.DateBoundaryType.DECLARED;
import static eu.europeana.normalization.dates.edtf.DateBoundaryType.OPEN;
import static eu.europeana.normalization.dates.edtf.DateBoundaryType.UNKNOWN;
import static eu.europeana.normalization.dates.edtf.InstantEdtfDateBuilder.THRESHOLD_4_DIGITS_YEAR;
import static eu.europeana.normalization.dates.edtf.Iso8601Parser.ISO_8601_MINIMUM_YEAR_DIGITS;
import static java.lang.Math.abs;
import static java.util.Optional.ofNullable;

import eu.europeana.normalization.dates.YearPrecision;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.TemporalAccessor;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a date used by EDM normalization, including partial dates and century or decade precision.
 * <p>
 * The builder multiplies a year prefix by its precision's duration. This class stores the expanded base year together with that
 * precision: prefix 19 with {@link YearPrecision#CENTURY} and prefix 190 with {@link YearPrecision#DECADE} both store year 1900,
 * as does the exact year 1900 with {@link YearPrecision#YEAR}. Their calculated bounds differ.
 * <p>
 * By project convention, century-precision values represent numbered calendar centuries. In particular, {@code 19XX} means the
 * 20th century, spanning 1901-01-01 through 2000-12-31. This is an intentional application interpretation, <b>rather than
 * expanding the unspecified digits into the possible years 1900-1999</b>. For common-era values, the normalization conventions
 * are:
 * <ul>
 *   <li>{@code 1900}: year precision, bounds 1900-01-01 through 1900-12-31, century link 19.</li>
 *   <li>{@code 19XX}, also produced from "20th century": century precision, bounds 1901-01-01 through 2000-12-31, century link 20.</li>
 *   <li>{@code 190X}: decade precision, bounds 1900-01-01 through 1909-12-31, century links 19 and 20.</li>
 * </ul>
 * <p>Normalization derives century links from {@link #getFirstDay()} and {@link #getLastDay()},
 * rather than from {@link #getCentury()} on the original object. The latter uses only the stored
 * base year and returns 19 for all three examples above.</p>
 * <p>The uncertain and approximate qualifiers, '?' and '~', are combined into '%' when applied together.</p>
 */
@Slf4j
@Getter
public final class InstantEdtfDate extends AbstractEdtfDate implements Comparable<InstantEdtfDate> {

  private Year year;
  private Month month;
  private LocalDate yearMonthDay;
  private Set<DateQualification> dateQualifications = EnumSet.noneOf(DateQualification.class);
  private YearPrecision yearPrecision;
  private DateBoundaryType dateBoundaryType = DECLARED;

  /**
   * Restricted constructor by provided {@link InstantEdtfDateBuilder}.
   * <p>All fields apart from {@link #dateQualifications} are strictly contained in the constructor. The date qualifications can
   * be further extended to, for example, add an approximate qualification for a date that was sanitized.</p>
   *
   * @param instantEdtfDateBuilder the builder with all content verified
   */
  InstantEdtfDate(InstantEdtfDateBuilder instantEdtfDateBuilder) {
    yearPrecision = instantEdtfDateBuilder.getYearPrecision();
    year = instantEdtfDateBuilder.getYearObj();
    month = instantEdtfDateBuilder.getMonthObj();
    yearMonthDay = instantEdtfDateBuilder.getYearMonthDayObj();
    dateQualifications = instantEdtfDateBuilder.getDateQualifications();
  }

  private InstantEdtfDate(DateBoundaryType dateBoundaryType) {
    this.dateBoundaryType = dateBoundaryType;
  }

  @Override
  public void addQualification(DateQualification dateQualification) {
    this.dateQualifications.add(dateQualification);
  }

  /**
   * Create an {@link DateBoundaryType#UNKNOWN} instant.
   *
   * @return the instant date created
   */
  public static InstantEdtfDate getUnknownInstance() {
    return new InstantEdtfDate(UNKNOWN);
  }

  /**
   * Create an {@link DateBoundaryType#OPEN} instant.
   *
   * @return the instant date created
   */
  public static InstantEdtfDate getOpenInstance() {
    return new InstantEdtfDate(OPEN);
  }

  /**
   * Returns the lower bound according to the date's precision and the normalization conventions described in this class. For
   * example, {@code 1900} starts on 1900-01-01, whereas century-precision {@code 19XX} starts on 1901-01-01.
   * Calculated bounds are allowed to lie in the future; future-date validation applies to the original input.
   *
   * @return the lower bound, or {@code null} for an open or unknown boundary or if construction fails
   * @see #firstDayOfYearDatePart()
   */
  @Override
  public InstantEdtfDate getFirstDay() {
    InstantEdtfDate firstDay = null;
    try {
      if (dateBoundaryType == DECLARED) {
        if (this.getYear().getValue() < -THRESHOLD_4_DIGITS_YEAR) {
          firstDay = new InstantEdtfDateBuilder(this.getYear().getValue()).buildBoundary();
        } else {
          firstDay = this.firstDayOfYearDatePart();
        }
      }
    } catch (DateExtractionException e) {
      log.error("Creating first day of instant failed!", e);
    }

    return firstDay;
  }

  /**
   * Computes the lower bound, retaining specified day and month components and filling missing ones.
   * <ul>
   *   <li>A complete date such as {@code 1989-11-01} is returned unchanged.</li>
   *   <li>A year-month such as {@code 1989-11} starts on 1989-11-01.</li>
   *   <li>A year-precision value such as {@code 1900} starts on 1900-01-01.</li>
   *   <li>A decade-precision value such as {@code 190X}, with stored base year 1900, starts on 1900-01-01.</li>
   *   <li>A century-precision value such as {@code 19XX}, with stored base year 1900, starts on 1901-01-01:
   *   the stored year plus one, following the numbered-century convention.</li>
   * </ul>
   *
   * @return the computed lower bound
   * @throws DateExtractionException if the builder rejects the computed date
   */
  private InstantEdtfDate firstDayOfYearDatePart() throws DateExtractionException {
    final TemporalAccessor temporalAccessorFirstDay;
    if (yearMonthDay != null) {
      temporalAccessorFirstDay = yearMonthDay;
    } else if (month != null) {
      temporalAccessorFirstDay = YearMonth.of(year.getValue(), month).atDay(1);
    } else {
      final MonthDay january01 = MonthDay.of(Month.JANUARY, 1);
      temporalAccessorFirstDay = year.plusYears(yearPrecision == YearPrecision.CENTURY ? 1 : 0).atMonthDay(january01);
    }

    return new InstantEdtfDateBuilder(temporalAccessorFirstDay).buildBoundary();
  }

  /**
   * Returns the upper bound according to the date's precision and the normalization conventions described in this class. For
   * example, {@code 1900} ends on 1900-12-31, whereas century-precision {@code 19XX} ends on 2000-12-31.
   * Calculated bounds are allowed to lie in the future. For example, an accepted {@code 20XX} retains its upper bound of
   * 2100-12-31 even before that day arrives; future-date validation applies to the original input.
   *
   * @return the upper bound, or {@code null} for an open or unknown boundary or if construction fails
   * @see #lastDayOfYearDatePart()
   */
  @Override
  public InstantEdtfDate getLastDay() {
    InstantEdtfDate lastDay = null;
    try {
      if (dateBoundaryType == DECLARED) {
        if (this.getYear().getValue() < -THRESHOLD_4_DIGITS_YEAR) {
          lastDay = new InstantEdtfDateBuilder(this.getYear().getValue()).buildBoundary();
        } else {
          lastDay = this.lastDayOfYearDatePart();
        }
      }
    } catch (DateExtractionException e) {
      log.error("Creating last day of instant failed!", e);
    }
    return lastDay;
  }

  /**
   * Computes the upper bound, retaining specified day and month components and filling missing ones.
   * <ul>
   *   <li>A complete date such as {@code 1989-11-01} is returned unchanged.</li>
   *   <li>A year-month such as {@code 1989-11} ends on 1989-11-30.</li>
   *   <li>A year-precision value such as {@code 1900} ends on 1900-12-31.</li>
   *   <li>A decade-precision value such as {@code 190X}, with stored base year 1900,
   *       ends on 1909-12-31: the stored year plus nine.</li>
   *   <li>A century-precision value such as {@code 19XX}, with stored base year 1900,
   *       ends on 2000-12-31: the stored year plus 100, following the numbered-century convention.</li>
   * </ul>
   *
   * @return the computed upper bound
   * @throws DateExtractionException if the builder rejects the computed date
   */
  private InstantEdtfDate lastDayOfYearDatePart() throws DateExtractionException {
    final TemporalAccessor temporalAccessorLastDay;
    if (yearMonthDay != null) {
      temporalAccessorLastDay = yearMonthDay;
    } else if (month != null) {
      temporalAccessorLastDay = YearMonth.of(year.getValue(), month).atEndOfMonth();
    } else {
      final Year adjustedYear;
      switch (yearPrecision) {
        case YearPrecision.CENTURY -> adjustedYear = year.plusYears(yearPrecision.getDuration());
        case YearPrecision.DECADE -> adjustedYear = year.plusYears(yearPrecision.getDuration() - 1L);
        default -> adjustedYear = year;
      }
      final MonthDay december31 = MonthDay.of(Month.DECEMBER, Month.DECEMBER.maxLength());
      temporalAccessorLastDay = adjustedYear.atMonthDay(december31);
    }
    return new InstantEdtfDateBuilder(temporalAccessorLastDay).buildBoundary();

  }

  @Override
  public boolean isOpen() {
    return dateBoundaryType == OPEN;
  }

  /**
   * Returns the century number calculated from the stored year, without considering year precision. For common-era dates, 1900
   * belongs to century 19 and 1901 belongs to century 20.
   * <p>
   * The original objects for {@code 1900}, {@code 19XX}, and {@code 190X} all store year 1900, so this method returns 19 for
   * each. This is not necessarily their normalized century classification: {@code 19XX} represents the 20th century by project
   * convention, while {@code 190X} spans both the 19th and 20th centuries.
   * <p>
   * To derive normalized century links, normalization calls this method on the bounds returned by {@link #getFirstDay()} and
   * {@link #getLastDay()}. Those boundary objects have year precision: the bounds of {@code 19XX} both return 20, while the
   * bounds of {@code 190X} return 19 and 20.
   *
   * @return the century number of the stored year
   */
  public Integer getCentury() {
    int centuryDivision = year.getValue() / YearPrecision.CENTURY.getDuration();
    int centuryModulo = year.getValue() % YearPrecision.CENTURY.getDuration();
    //For case 1900 it is 19th. For case 1901 it is 20th century
    return (centuryModulo == 0) ? centuryDivision : (centuryDivision + 1);
  }

  /**
   * Adjusts a year with padding and optional precision that replace right most digits with 'X's.
   * <p>
   * There are two possibilities:
   *   <ul>
   *     <li>The year is precise therefore, it will be left padded with 0 to the max of 4 digits in total</li>
   *     <li>The year is not precise which will be left padded with 0 to the max of 4 digits in total and then the right most
   *     digits are replaced with 'X's based on the year precision. E.g., a year -900 with century precision will become -09XX</li>
   *   </ul>
   * </p>
   *
   * @return the adjusted year
   */
  private String serializeYear() {
    final DecimalFormat decimalFormat = new DecimalFormat("0000");
    final String paddedYear = decimalFormat.format(Math.abs(year.getValue()));

    final String prefix = year.getValue() < 0 ? "-" : "";
    final int trailingZeros = Integer.numberOfTrailingZeros(yearPrecision.getDuration());
    final String yearAdjusted = paddedYear.substring(0, ISO_8601_MINIMUM_YEAR_DIGITS - trailingZeros) + "X".repeat(trailingZeros);
    return prefix + yearAdjusted;
  }

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder();
    if (dateBoundaryType != DECLARED) {
      stringBuilder.append(dateBoundaryType.getSerializedRepresentation());
    } else if (abs(year.getValue()) > THRESHOLD_4_DIGITS_YEAR) {
      stringBuilder.append(InstantEdtfDateBuilder.OVER_4_DIGITS_YEAR_PREFIX).append(year.getValue());
    } else {
      stringBuilder.append(serializeYear());

      final DecimalFormat decimalFormat = new DecimalFormat("00");
      stringBuilder.append(
          ofNullable(month).map(Month::getValue).map(decimalFormat::format).map(m -> "-" + m).orElse(""));
      stringBuilder.append(
          ofNullable(yearMonthDay).map(LocalDate::getDayOfMonth).map(decimalFormat::format).map(d -> "-" + d).orElse(""));
    }
    stringBuilder.append(DateQualification.getCharacterFromQualifications(dateQualifications));
    return stringBuilder.toString();
  }

  @Override
  public int compareTo(InstantEdtfDate other) {
    int comparatorValue = this.year.compareTo(other.year);
    if (comparatorValue == 0 && this.month != null && other.month != null) {
      comparatorValue = this.month.compareTo(other.month);
      if (comparatorValue == 0 && this.yearMonthDay != null && other.yearMonthDay != null) {
        comparatorValue = this.yearMonthDay.compareTo(other.yearMonthDay);
      }
    }
    return comparatorValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InstantEdtfDate that = (InstantEdtfDate) o;
    return yearPrecision == that.yearPrecision && Objects.equals(year, that.year) && Objects.equals(month,
        that.month) && Objects.equals(yearMonthDay, that.yearMonthDay) && dateQualifications == that.dateQualifications
        && dateBoundaryType == that.dateBoundaryType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(yearPrecision, year, month, yearMonthDay, dateQualifications, dateBoundaryType);
  }

  @Override
  public Set<DateQualification> getDateQualifications() {
    return EnumSet.copyOf(dateQualifications);
  }

}
