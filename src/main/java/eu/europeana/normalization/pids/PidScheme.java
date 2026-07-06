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
   * Find the first PID in this input.
   *
   * @param input The input in which to discover PIDs. Cannot be <code>null<code>.
   * @return The match result. Is <code>null</code> exactly if the input does not match this scheme.
   */
  public PidSingleMatchResult find(String input) {
    final OptimalMatch<Matcher> optimalMatch = new OptimalMatch<>(matcher ->
        new MatchedSegment(matcher.start(), matcher.end()));
    for (Pattern pattern : matchingPatterns) {
      final Matcher matcher = pattern.matcher(input);
      if (matcher.find()) {
        optimalMatch.submitAlternative(matcher);
      }
    }
    return resultFromSuccessfulMatcher(optimalMatch.getCurrentOptimum());
  }

  /**
   * Match a PID candidate against this scheme.
   *
   * @param pidCandidate The PID candidate to match. Cannot be <code>null<code>.
   * @return The match result. Is <code>null</code> exactly if the PID does not match this scheme.
   */
  public PidSingleMatchResult match(String pidCandidate) {
    return matchingPatterns.stream().map(pattern -> pattern.matcher(pidCandidate))
        .filter(Matcher::matches).map(this::resultFromSuccessfulMatcher).findFirst().orElse(null);
  }

  /**
   * Convert a successful {@link Matcher} (that has had a successful match or find) to a match
   * result.
   *
   * @param matcher The {@link Matcher} instance.
   * @return The match result.
   */
  private PidSingleMatchResult resultFromSuccessfulMatcher(Matcher matcher) {

    // Null check
    if (matcher == null) {
      return null;
    }

    // Extract information from the match.
    final String originalPid = matcher.group();
    final int start = matcher.start();
    final int end = matcher.end();
    final String canonicalForm = this.canonicalPattern == null ? originalPid :
        RegexUtils.copyGroupsToTemplate(matcher, this.canonicalPattern);

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
