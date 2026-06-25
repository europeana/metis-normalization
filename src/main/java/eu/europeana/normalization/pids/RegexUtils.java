package eu.europeana.normalization.pids;

import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Provides utility methods for handling string transformation using regular expressions.
 */
public final class RegexUtils {

  /**
   * Constructor, not meant to be used. This class should not be instantiated.
   */
  private RegexUtils() {}

  /**
   * Utilities method for copying the groups from the matcher into the template.
   *
   * @param matcher  The matcher. The caller should have already called <code>match()</code> or
   *                 <code>find()</code> and check that a match was indeed found. This means that
   *                 this method expects the groups to be available for retrieval.
   * @param template The template to copy the groups into. It contains placeholders of the form
   *                 ${group_number} where the group numbers are 1-based.
   * @return The resolved template with all groups.
   */
  public static String copyGroupsToTemplate(Matcher matcher, String template) {
    String result = template;
    for (int grp = 1; grp <= matcher.groupCount(); grp++) {
      result = result.replace("${" + grp + "}",
          Optional.ofNullable(matcher.group(grp)).orElse(""));
    }
    return result;
  }
}
