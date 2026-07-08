package eu.europeana.normalization.pids;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Multiple matches of the same PID against the same scheme in the vocabulary.
 */
public final class PidMultipleMatchResult {

  private final String canonicalPid;
  private final String schemeId;
  private final Set<String> resolvablePids = new HashSet<>();
  private final Set<String> originalPids = new HashSet<>();

  /**
   * Constructor.
   *
   * @param schemeId     The PID scheme that matched the PID. Is not <code>null</code>.
   * @param canonicalPid The canonical version of the PID. May be <code>null</code>.
   */
  private PidMultipleMatchResult(String schemeId, String canonicalPid) {
    this.schemeId = schemeId;
    this.canonicalPid = canonicalPid;
  }

  /**
   * Merges another PID into this one. The condition is that the other PID is a subset of this PID
   * (i.e., the canonical value of the other PID starts with or is equal to the canonical value of
   * this PID). This PID's scheme will be retained. Resolvable PID values will only be merged if the
   * canonical values are equal.
   *
   * @param otherPid The PID to merge into this one.
   */
  public void merge(PidMultipleMatchResult otherPid) {
    if (!otherPid.canonicalPid.startsWith(this.canonicalPid)) {
      throw new IllegalArgumentException("Cannot merge different PIDs.");
    }
    if (otherPid.canonicalPid.equals(this.canonicalPid)) {
      this.resolvablePids.addAll(otherPid.resolvablePids);
    }
    this.originalPids.addAll(otherPid.originalPids);
  }

  /**
   * Combines multiple results into one instance of this class.
   *
   * @param results The individual results that were obtained. Cannot be <code>null</code>. No
   *                property of any result can be <code>null</code>.
   * @return An instance of this class, if there is at least one input result, and all input results
   * have the same canonical PID and PID scheme. Otherwise, return <code>null</code>. This will
   * not return null for a list of size 1.
   */
  public static PidMultipleMatchResult forResults(List<PidSingleMatchResult> results) {

    // Get the canonical PIDs. If there are none or multiple, we are done.
    final Set<String> canonicalPids = results.stream().map(PidSingleMatchResult::canonicalPid)
        .collect(Collectors.toSet());
    if (canonicalPids.size() != 1) {
      return null;
    }

    // Get all scheme IDs. If there are none or multiple, we are done. Less likely now.
    final Set<String> schemes = results.stream().map(PidSingleMatchResult::scheme)
        .map(PidSchemeInfo::getSchemeId).collect(Collectors.toSet());
    if (schemes.size() != 1) {
      return null;
    }

    // Compile the result.
    final PidMultipleMatchResult result = new PidMultipleMatchResult(
        results.getFirst().scheme().getSchemeId(), results.getFirst().canonicalPid());
    results.stream().map(PidSingleMatchResult::resolvablePid).forEach(result.resolvablePids::add);
    results.stream().map(PidSingleMatchResult::originalPid).forEach(result.originalPids::add);
    return result;
  }

  public String getSchemeId() {
    return schemeId;
  }

  public String getCanonicalPid() {
    return canonicalPid;
  }

  public Set<String> getResolvablePids() {
    return Collections.unmodifiableSet(resolvablePids);
  }

  public Set<String> getOriginalPids() {
    return Collections.unmodifiableSet(originalPids);
  }
}
