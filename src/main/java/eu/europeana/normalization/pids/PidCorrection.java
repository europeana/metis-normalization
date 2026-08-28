package eu.europeana.normalization.pids;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This object corresponds to a PID correction scheme.
 *
 * @param pattern    The pattern of this correction scheme that an input string needs to satisfy.
 *                   The pattern should contain groups that will be inserted into the correction.
 * @param correction The correction to apply. There should be placeholders for group insertion in
 *                   the correction of the form ${group_number}, where the group numbers are
 *                   1-based.
 */
public record PidCorrection(Pattern pattern, String correction) {

  /**
   * Convenience constructor.
   *
   * @param pattern       The pattern in string form.
   * @param caseSensitive Whether to apply the pattern matching in a case-sensitive
   *                      (<code>true</code>) or case-insensitive (<code>false</code>) manner.
   * @param correction    The correction.
   */
  public PidCorrection(String pattern, boolean caseSensitive, String correction) {
    this(Pattern.compile(pattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE), correction);
  }

  /**
   * Attempt to correct a PID candidate value according this correction scheme.
   *
   * @param pidCandidate The value to correct.
   * @return The corrected value.
   */
  public String attemptCorrection(String pidCandidate) {

    // See if the candidate matches this correction scheme. If not, return the input unchanged.
    final Matcher matcher = pattern.matcher(pidCandidate);
    if (!matcher.matches()) {
      return pidCandidate;
    }

    // Compile the correction by inserting the groups into the right place.
    return RegexUtils.copyGroupsToTemplate(matcher, this.correction);
  }
}
