package eu.europeana.normalization.pids;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;

/**
 * Provides utility methods for handling string transformation using regular expressions.
 */
public final class RegexUtils {

  /**
   * Represents a matched segment by a regex in some input. There is an implied order: a matched
   * segment improves on another segment if it is earlier in the input or, if there is a tie, if the
   * match covers a larger segment.
   *
   * @param start The first index in the input that is in the segment (inclusive).
   * @param end   The first index in the input after the segment (exclusive).
   */
  public record MatchedSegment(int start, int end) {

    /**
     * Computes whether this match improves on the other match. Any match improves on a
     * <code>null</code> match.
     *
     * @param otherSegment The other matched segment.
     * @return Whether this match improves on the other one.
     */
    boolean improvesOn(MatchedSegment otherSegment) {
      return otherSegment == null || otherSegment.start() > this.start()
          || ((otherSegment.start() == this.start()) && (otherSegment.end() < this.end()));
    }
  }

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

  /**
   * Keeps track of the optimal match. Alternatives can be submitted, which leads to the better one
   * being kept and the lesser one being discarded.
   *
   * @param <T> The object type representing the match.
   */
  public static class OptimalMatch<T> {

    private final Function<T, MatchedSegment> segmentExtractor;
    private T currentOptimum = null;

    /**
     * Constructor.
     * @param segmentExtractor Function for converting to an instance of {@link MatchedSegment}.
     */
    public OptimalMatch(Function<T, MatchedSegment> segmentExtractor) {
      this.segmentExtractor = segmentExtractor;
    }

    /**
     * Submit the match for consideration. If it improves on the current optimum, it becomes the new
     * optimum.
     *
     * @param match The match to consider. Can be <code>null</code>.
     */
    public void submitAlternative(T match) {
      if (match != null) {
        final MatchedSegment currentSegment = Optional.ofNullable(currentOptimum)
            .map(segmentExtractor).orElse(null);
        currentOptimum =
            segmentExtractor.apply(match).improvesOn(currentSegment) ? match : currentOptimum;
      }
    }

    public T getCurrentOptimum() {
      return currentOptimum;
    }
  }
}
