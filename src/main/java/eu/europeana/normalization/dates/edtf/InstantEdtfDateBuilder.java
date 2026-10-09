package eu.europeana.normalization.dates.edtf;

import eu.europeana.normalization.dates.YearPrecision;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Builder class for {@link InstantEdtfDate}.
 * <p>Years with an absolute value greater than {@link #THRESHOLD_4_DIGITS_YEAR} are recognized automatically from the numeric
 * year after applying its precision. Extractors validate the input notation, including the EDTF {@code Y} prefix, before
 * passing numeric values to this builder. Long years remain exempt from the strict profile's complete-date requirement.
 * <p>During {@link #build()} it will verify all the parameters that have been requested.
 * The {@link #build()}, if {@link #withAllowDayMonthSwap(boolean)} was called with {@code true}, will also attempt a second time
 * by switching month and day values if the original values were invalid. Furthermore, there are a set of constructors that can
 * start the builder and will perform a build with specific characteristics:
 * <ul>
 *   <li>{@link InstantEdtfDateBuilder#InstantEdtfDateBuilder(Integer)} which initializes the builder with the minimum requirement of year value.</li>
 *   <li>{@link InstantEdtfDateBuilder#InstantEdtfDateBuilder(TemporalAccessor)} can be used instead to pass multiple values through a {@link TemporalAccessor}.
 *   This object during build will overwrite the date parts, if any <@code>.with</@code> methods were called, from the {@link TemporalAccessor}</li>
 * </ul>
 */
@Slf4j
@Getter
public class InstantEdtfDateBuilder {

  public static final int THRESHOLD_4_DIGITS_YEAR = 9999;
  public static final char OVER_4_DIGITS_YEAR_PREFIX = 'Y';
  private Year yearObj;
  private Month monthObj;
  private LocalDate yearMonthDayObj;
  private final Integer year;
  private Integer month;
  private Integer day;
  private YearPrecision yearPrecision = YearPrecision.YEAR;
  private final Set<DateQualification> dateQualifications = EnumSet.noneOf(DateQualification.class);
  private boolean allowDayMonthSwap = true;
  private Clock clock = Clock.systemDefaultZone();

  /**
   * Constructor that initializes the builder with the minimum requirement of year value.
   *
   * @param year the year value
   */
  public InstantEdtfDateBuilder(final Integer year) {
    this.year = year;
  }

  /**
   * Constructor with {@link TemporalAccessor}.
   * <p>This object during build will overwrite the date parts, if any <@code>.with</@code> methods were called, from the
   * {@link TemporalAccessor}
   * </p>
   *
   * @param temporalAccessor the temporal accessor
   */
  public InstantEdtfDateBuilder(TemporalAccessor temporalAccessor) {
    day = temporalAccessor.isSupported(ChronoField.DAY_OF_MONTH) ?
        temporalAccessor.get(ChronoField.DAY_OF_MONTH) : null;
    month = temporalAccessor.isSupported(ChronoField.MONTH_OF_YEAR) ?
        temporalAccessor.get(ChronoField.MONTH_OF_YEAR) : null;
    year = temporalAccessor.isSupported(ChronoField.YEAR) ?
        temporalAccessor.get(ChronoField.YEAR) : null;
  }

  /**
   * Returns an instance of {@link InstantEdtfDate} created and validated from the fields set on this builder.
   *
   * @return the new instant edtf date
   * @throws DateExtractionException if something went wrong during date validation
   */
  public InstantEdtfDate build() throws DateExtractionException {
    return build(true);
  }

  /**
   * Builds a calculated boundary of an already validated date. Calendar, year-range, and strict-profile validation still apply,
   * but a calculated boundary may lie in the future when the source date has coarse precision.
   *
   * @return the calculated boundary
   * @throws DateExtractionException if the boundary is invalid
   */
  InstantEdtfDate buildBoundary() throws DateExtractionException {
    return build(false);
  }

  private InstantEdtfDate build(boolean validateFutureDate) throws DateExtractionException {
    InstantEdtfDate instantEdtfDate = buildInternal(validateFutureDate);
    //Try once more if flexible date
    if (instantEdtfDate == null && isPositive(month) && isPositive(day) && allowDayMonthSwap) {
      swapMonthDay();
      instantEdtfDate = buildInternal(validateFutureDate);
    }

    //Still nothing, we are done.
    if (instantEdtfDate == null) {
      throw new DateExtractionException("Could not instantiate date");
    }
    return instantEdtfDate;
  }

  private InstantEdtfDate buildInternal(boolean validateFutureDate) {
    InstantEdtfDate instantEdtfDate = null;
    try {
      parseYear();
      parseMonthDay();
      if (validateFutureDate) {
        validateDateNotInFuture();
      }
      validateStrict();
      instantEdtfDate = new InstantEdtfDate(this);
    } catch (DateTimeException | DateExtractionException e) {
      log.debug("Date build failed.", e);
    }
    return instantEdtfDate;
  }

  private void parseYear() {
    Objects.requireNonNull(year, "Year value can never be null");
    yearObj = Year.of(ChronoField.YEAR.checkValidIntValue((long) year * yearPrecision.getDuration()));
  }

  private void parseMonthDay() throws DateExtractionException {
    try {
      if (isPositive(month)) {
        monthObj = Month.of(month);
        if (isPositive(day)) {
          yearMonthDayObj = LocalDate.of(yearObj.getValue(), monthObj.getValue(), day);
        }
      }
    } catch (DateTimeException e) {
      throw new DateExtractionException("Failed to instantiate month and day", e);
    }
  }

  private boolean isPositive(Integer value) {
    return value != null && value > 0;
  }

  private void validateDateNotInFuture() throws DateExtractionException {
    try {
      final LocalDate today = LocalDate.now(clock);
      final boolean isYearMonthDayInTheFuture = yearMonthDayObj != null && yearMonthDayObj.isAfter(today);
      final boolean isYearMonthInTheFuture = monthObj != null
          && YearMonth.of(yearObj.getValue(), month).isAfter(YearMonth.from(today));
      final boolean isYearInTheFuture = yearObj != null && yearObj.isAfter(Year.from(today));

      if (isYearMonthDayInTheFuture || isYearMonthInTheFuture || isYearInTheFuture) {
        throw new DateExtractionException("Date cannot be in the future");
      }

    } catch (DateTimeException e) {
      throw new DateExtractionException("Failed to instantiate month and day", e);
    }
  }

  private void validateStrict() throws DateExtractionException {
    //If it is not a long year, and we want to be strict we further validate
    boolean isFourDigitYearAndStrictBuild = Math.abs(yearObj.getValue()) <= THRESHOLD_4_DIGITS_YEAR
        && !allowDayMonthSwap;
    boolean isDateNonPrecise =
        dateQualifications.contains(DateQualification.UNCERTAIN) || (yearPrecision != null
            && yearPrecision != YearPrecision.YEAR);
    boolean notCompleteDate = monthObj == null || yearMonthDayObj == null;
    if (isFourDigitYearAndStrictBuild && (isDateNonPrecise || notCompleteDate)) {
      throw new DateExtractionException("Date is invalid according to our strict profile!");
    }
  }

  private void swapMonthDay() {
    Integer tempMonth = month;
    month = day;
    day = tempMonth;
  }

  /**
   * Optionally overrides the clock used for future-date validation. Defaults to the system clock in the default time zone.
   *
   * @param clock the clock, including the time zone used for the current date
   * @return the updated builder
   */
  public InstantEdtfDateBuilder withClock(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    return this;
  }

  /**
   * Add month value.
   *
   * @param month the month value
   * @return the extended builder
   */
  public InstantEdtfDateBuilder withMonth(int month) {
    this.month = month;
    return this;
  }

  /**
   * Add day value.
   *
   * @param day the day value
   * @return the extended builder
   */
  public InstantEdtfDateBuilder withDay(int day) {
    this.day = day;
    return this;
  }

  /**
   * Add year precision.
   *
   * @param yearPrecision the year precision
   * @return the extended builder
   */
  public InstantEdtfDateBuilder withYearPrecision(YearPrecision yearPrecision) {
    this.yearPrecision = yearPrecision;
    return this;
  }

  /**
   * Add date qualification.
   *
   * @param dateQualifications the date qualifications
   * @return the extended builder
   */
  public InstantEdtfDateBuilder withDateQualification(Set<DateQualification> dateQualifications) {
    this.dateQualifications.addAll(dateQualifications);
    return this;
  }

  /**
   * Opt in/out for day month swap if original values failed validation.
   *
   * @param allowDayMonthSwap the boolean (dis|en)abling the day and month swap
   * @return the extended builder
   */
  public InstantEdtfDateBuilder withAllowDayMonthSwap(boolean allowDayMonthSwap) {
    this.allowDayMonthSwap = allowDayMonthSwap;
    return this;
  }

  public Set<DateQualification> getDateQualifications() {
    return EnumSet.copyOf(dateQualifications);
  }
}
