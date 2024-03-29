package eu.europeana.normalization.dates.extraction.extractors;

import static eu.europeana.normalization.dates.edtf.DateBoundaryType.OPEN;
import static eu.europeana.normalization.dates.edtf.DateBoundaryType.UNKNOWN;
import static eu.europeana.normalization.dates.edtf.DateQualification.APPROXIMATE;
import static eu.europeana.normalization.dates.edtf.DateQualification.UNCERTAIN;
import static eu.europeana.normalization.dates.extraction.DefaultDatesSeparator.SLASH_DELIMITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.europeana.normalization.dates.DateNormalizationExtractorMatchId;
import eu.europeana.normalization.dates.DateNormalizationResult;
import eu.europeana.normalization.dates.DateNormalizationResultStatus;
import eu.europeana.normalization.dates.edtf.AbstractEdtfDate;
import eu.europeana.normalization.dates.edtf.InstantEdtfDate;
import eu.europeana.normalization.dates.edtf.IntervalEdtfDate;

public interface DateExtractorTest {

  default void assertQualification(String expected, InstantEdtfDate instantEdtfDate) {
    assertEquals(expected.contains("?"),
        instantEdtfDate.getDateQualifications().contains(UNCERTAIN) &&
            !instantEdtfDate.getDateQualifications().contains(APPROXIMATE));
    assertEquals(expected.contains("~"),
        instantEdtfDate.getDateQualifications().contains(APPROXIMATE) &&
            !instantEdtfDate.getDateQualifications().contains(UNCERTAIN));
    assertEquals(expected.contains("%"),
        instantEdtfDate.getDateQualifications().contains(UNCERTAIN) &&
            instantEdtfDate.getDateQualifications().contains(APPROXIMATE));
  }

  default void assertBoundaryType(String expected, InstantEdtfDate instantEdtfDate) {
    assertEquals(expected.equals(OPEN.getSerializedRepresentation()),
        instantEdtfDate.getDateBoundaryType() == OPEN || instantEdtfDate.getDateBoundaryType() == UNKNOWN);
  }

  default void assertDateNormalizationResult(DateNormalizationResult dateNormalizationResult, String expected,
      DateNormalizationExtractorMatchId dateNormalizationExtractorMatchId, String expectedLabel) {
    assertDateNormalizationResult(dateNormalizationResult, expected, dateNormalizationExtractorMatchId);
    if (expected != null) {
      assertEquals(expectedLabel, dateNormalizationResult.getEdtfDate().getLabel());
    }
  }

  default void assertDateNormalizationResult(DateNormalizationResult dateNormalizationResult, String expected,
      DateNormalizationExtractorMatchId dateNormalizationExtractorMatchId) {
    if (expected == null) {
      assertEquals(DateNormalizationResultStatus.NO_MATCH, dateNormalizationResult.getDateNormalizationResultStatus());
    } else {
      assertEquals(dateNormalizationExtractorMatchId, dateNormalizationResult.getDateNormalizationExtractorMatchId());
      AbstractEdtfDate edtfDate = dateNormalizationResult.getEdtfDate();
      if (edtfDate instanceof IntervalEdtfDate) {
        String expectedStart = expected.substring(0, expected.indexOf(SLASH_DELIMITER.getStringRepresentation()));
        String expectedEnd = expected.substring(expected.indexOf(SLASH_DELIMITER.getStringRepresentation()) + 1);
        InstantEdtfDate startInstantEdtfDate = ((IntervalEdtfDate) edtfDate).getStart();
        InstantEdtfDate endInstantEdtfDate = ((IntervalEdtfDate) edtfDate).getEnd();
        assertQualification(expectedStart, startInstantEdtfDate);
        assertQualification(expectedEnd, endInstantEdtfDate);
        assertBoundaryType(expectedStart, startInstantEdtfDate);
        assertBoundaryType(expectedEnd, endInstantEdtfDate);
      } else {
        assertQualification(expected, (InstantEdtfDate) edtfDate);
      }
      assertEquals(expected, edtfDate.toString());
    }
  }
}
