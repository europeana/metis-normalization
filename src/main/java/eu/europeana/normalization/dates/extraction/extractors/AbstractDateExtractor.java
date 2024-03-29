package eu.europeana.normalization.dates.extraction.extractors;

import static eu.europeana.normalization.dates.DateNormalizationResult.getNoMatchResult;
import static eu.europeana.normalization.dates.edtf.DateQualification.UNCERTAIN;
import static java.lang.String.format;

import eu.europeana.normalization.dates.DateNormalizationResult;
import eu.europeana.normalization.dates.edtf.DateQualification;
import eu.europeana.normalization.dates.extraction.DateExtractionException;
import eu.europeana.normalization.dates.sanitize.DateFieldSanitizer;
import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class implementing interface {@link DateExtractor} with default functionality for all extractors
 */
public abstract class AbstractDateExtractor implements DateExtractor {

  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  static final String OPTIONAL_QUESTION_MARK_REGEX = "\\??";

  /**
   * Reusable default checking of Date qualification on an input.
   *
   * @param inputValue the input value
   * @return the date qualification
   */
  public Set<DateQualification> getQualification(String inputValue) {
    final Set<DateQualification> dateQualifications = EnumSet.noneOf(DateQualification.class);
    if (inputValue.startsWith("?") || inputValue.endsWith("?")) {
      dateQualifications.add(UNCERTAIN);
    }
    return dateQualifications;
  }

  /**
   * Utility method for calling {@link DateExtractor#extract(String, boolean)} with flexibleDateBuild as true.
   * <p>It also captures relevant exceptions so that return is performed</p>
   *
   * @param inputValue the input value
   * @return the date normalization result
   */
  @Override
  public DateNormalizationResult extractDateProperty(String inputValue) {
    return getDateNormalizationResult(inputValue, true);
  }

  /**
   * Utility method for calling {@link DateExtractor#extract(String, boolean)} with flexibleDateBuild as false.
   * <p>It also captures relevant exceptions so that return is performed</p>
   *
   * @param inputValue the input value
   * @return the date normalization result
   */
  @Override
  public DateNormalizationResult extractGenericProperty(String inputValue) {
    return getDateNormalizationResult(inputValue, false);
  }

  private DateNormalizationResult getDateNormalizationResult(String inputValue, boolean flexibleDateBuild) {
    final String sanitizedValue = DateFieldSanitizer.cleanSpacesAndTrim(inputValue);
    DateNormalizationResult dateNormalizationResult;
    try {
      dateNormalizationResult = extract(sanitizedValue, flexibleDateBuild);
    } catch (DateExtractionException e) {
      LOGGER.debug(format("Date extraction failed %s: ", sanitizedValue), e);
      dateNormalizationResult = getNoMatchResult(inputValue);
    }

    return dateNormalizationResult;
  }
}
