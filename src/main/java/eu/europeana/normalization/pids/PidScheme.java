package eu.europeana.normalization.pids;

import eu.europeana.normalization.pids.RegexUtils.MatchedSegment;
import eu.europeana.normalization.pids.RegexUtils.OptimalMatch;
import eu.europeana.normalization.pids.model.PersistentIdentifierScheme;
import eu.europeana.normalization.pids.model.PersistentIdentifierScheme.Resource;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a PID scheme that can be matched against.
 */
public class PidScheme implements PidSchemeInfo, Comparable<PidScheme> {

  private final String schemeId;
  private final Set<Pattern> matchingPatterns = new HashSet<>();
  private final String canonicalPattern;
  private final String resolvablePattern;
  private final String title;
  private final String seeAlso;
  private final String organization;

  /**
   * Constructor.
   *
   * @param loadedScheme The scheme as it is represented in the vocabulary file.
   */
  public PidScheme(PersistentIdentifierScheme loadedScheme) {
    this(loadedScheme.getAbout(), loadedScheme.getMatchingPatterns(),
        loadedScheme.getCanonicalPattern(), loadedScheme.getResolvablePattern(),
        loadedScheme.getTitle(),
        Optional.ofNullable(loadedScheme.getSeeAlso()).map(Resource::getResource).orElse(null),
        loadedScheme.getMaintainer());
  }

  /**
   * Constructor for test objects
   *
   * @param schemeId          The scheme ID
   * @param matchingPatterns  The Matching patterns.
   * @param canonicalPattern  The canonical pattern.
   * @param resolvablePattern The resolvable pattern.
   * @param title             The title.
   * @param seeAlso           A See Also value.
   * @param organization      The organisation.
   */
  PidScheme(String schemeId,Set<String> matchingPatterns , String canonicalPattern,
      String resolvablePattern, String title, String seeAlso, String organization) {
    this.schemeId = schemeId;
    Optional.ofNullable(matchingPatterns).stream().flatMap(Collection::stream)
        .forEach(pattern -> this.matchingPatterns.add(
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)));
    this.canonicalPattern = canonicalPattern;
    this.resolvablePattern = resolvablePattern;
    this.title = title;
    this.seeAlso = seeAlso;
    this.organization = organization;
  }

  /**
   * Tries to find one of the patterns in the provided input. If multiple patterns are matched,
   * we try to find the one that matches as early in the input as possible. If there is still
   * a tie, we try to find the longest match.
   *
   * @param input The input string from which to extract PIDs.
   * @return A matcher based on one of the matching patterns that was found to match the input.
   * Returns <code>null</code> if no pattern matched.
   */
  private Matcher getSuccessfulMatch(String input) {
    final OptimalMatch<Matcher> optimalMatch = new OptimalMatch<>(matcher ->
        new MatchedSegment(matcher.start(), matcher.end()));
    for (Pattern pattern : matchingPatterns) {
      final Matcher matcher = pattern.matcher(input);
      if (matcher.find()) {
        optimalMatch.submitAlternative(matcher);
      }
    }
    return optimalMatch.getCurrentOptimum();
  }

  /**
   * Match a PID against this scheme.
   *
   * @param input The PID to match.
   * @return The match result. Is <code>null</code> exactly if the PID does not match this scheme.
   */
  public PidSingleMatchResult match(String input) {

    // Try to find a match. If we fail, we are done.
    final Matcher match = getSuccessfulMatch(input);
    if (match == null) {
      return null;
    }

    // Extract information from the match.
    final String originalPid = match.group();
    final int start = match.start();
    final int end = match.end();
    final String canonicalForm = this.canonicalPattern == null ? originalPid :
        RegexUtils.copyGroupsToTemplate(match, this.canonicalPattern);

    // Compile the result.
    final String resolvableForm = Optional.ofNullable(this.resolvablePattern)
        .map(pattern -> pattern.replace("${0}", canonicalForm)).orElse(originalPid);
    return new PidSingleMatchResult(this, canonicalForm, resolvableForm, originalPid, start, end);
  }

  @Override
  public int compareTo(PidScheme o) {
    return schemeId.compareTo(o.schemeId);
  }

  @Override
  public String getSchemeId() {
    return schemeId;
  }

  @Override
  public String getSeeAlso() {
    return seeAlso;
  }

  @Override
  public String getTitle() {
    return title;
  }

  @Override
  public String getOrganization() {
    return organization;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PidScheme pidScheme = (PidScheme) o;
    return Objects.equals(schemeId, pidScheme.schemeId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(schemeId);
  }
}
