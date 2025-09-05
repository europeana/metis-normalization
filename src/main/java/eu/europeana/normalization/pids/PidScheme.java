package eu.europeana.normalization.pids;

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
        loadedScheme.getTitle(), loadedScheme.getSeeAlso(), loadedScheme.getMaintainer());
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
   * Transforms a PID string into its canonical form.
   *
   * @param pid a valid PID string of this scheme. Cannot be null.
   * @return the canonical form of the PID. If the PID is already in its canonical form, or this
   * scheme does not have a canonical form, then the same PID is returned. If the PID does not match
   * this scheme, <code>null</code> is returned.
   */
  private String getCanonicalForm(String pid) {

    // Try to match against any of the defined patterns. Otherwise, we are done.
    final Matcher successfulMatcher = matchingPatterns.stream()
        .map(pattern -> pattern.matcher(pid))
        .filter(Matcher::matches).findFirst().orElse(null);
    if (successfulMatcher == null) {
      return null;
    }

    // Construct the canonical form based on the matched pattern.
    if (this.canonicalPattern == null) {
      return pid;
    }
    String result = this.canonicalPattern;
    for (int grp = 1; grp <= successfulMatcher.groupCount(); grp++) {
      result = result.replace("$" + grp,
          Optional.ofNullable(successfulMatcher.group(grp)).orElse(""));
    }
    return result;
  }

  /**
   * Match a PID against this scheme.
   *
   * @param pid The PID to match.
   * @return The match result. Is <code>null</code> exactly if the PID does not match this scheme.
   */
  public PidMatchResult match(String pid) {
    final String trimmedPid = Objects.requireNonNull(pid, "pid must not be null").trim();
    final String canonicalForm = getCanonicalForm(trimmedPid);
    if (canonicalForm == null) {
      return null;
    }
    final String resolvableForm = Optional.ofNullable(this.resolvablePattern)
        .map(pattern -> pattern.replace("$0", canonicalForm)).orElse(trimmedPid);
    return new PidMatchResult(this, canonicalForm, resolvableForm, trimmedPid);
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
