package eu.europeana.normalization.pids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * This class maintains a collection of discovered PIDs. It represents all persistent identifier
 * objects discovered in a given resource (as opposed to those provided in <code>edm:pid</code>
 * literals/references). It can be added to, deduplicated and then written to an instance of
 * {@link NormalizedPidsForRecord}.
 * </p>
 * <p>
 * Note that we merge PIDs if canonical values are prefixes: if a shorter version of a canonical
 * value is found, we prefer that and merge information form the longer version into the shorter
 * version. However, instead of maintaining a trie (a.k.a. prefix tree), we perform the merging at
 * the time of adding.
 * </p>
 */
public class DiscoveredPidsForResource {

  // Map of PIDs by their canonical value. We guarantee at the time of adding that no PID in this
  // map is a prefix of another PID in this map.
  private final Map<String, PidMultipleMatchResult> pids = new HashMap<>();

  /**
   * Add the new PID to this collection. Ensure that we merge it with known PIDs if one is a prefix
   * of the other. We only retain the shortest version.
   *
   * @param newPid The new PID to add to this collection. Is not <code>null</code>.
   */
  public void addPid(PidMultipleMatchResult newPid) {

    // Check if any known PID is a prefix of the new PID (or they are equal). If so, we just need to
    // merge it into the new PID. Note: no two known PIDs can be a prefix of the new PID. One of the
    // two known PIDs would have had to be a prefix of the other, which is not possible.
    for (PidMultipleMatchResult knownPid : pids.values()) {
      if (newPid.getCanonicalPid().startsWith(knownPid.getCanonicalPid())) {
        knownPid.merge(newPid);
        return;
      }
    }

    // So the PID constitutes new information. Before adding it, check whether it is a prefix for
    // PIDs we already know. We remove these from the map and merge them into this new PID.
    // Make a copy of the value set, as we may be removing entries from the map.
    for (PidMultipleMatchResult knownPid : new ArrayList<>(pids.values())) {
      if (knownPid.getCanonicalPid().startsWith(newPid.getCanonicalPid())) {
        newPid.merge(knownPid);
        pids.remove(knownPid.getCanonicalPid());
      }
    }
    pids.put(newPid.getCanonicalPid(), newPid);
  }

  /**
   * Write the contents of this collection to the record.
   *
   * @param recordPids The record to which to write. Is not <code>null</code>.
   * @return The set of PID object references that are to be set in the resource as
   * <code>edm:pid</code> references (overwriting the current list of such references and
   * literals). Is not <code>null</code>.
   */
  public Set<String> writeToRecord(NormalizedPidsForRecord recordPids) {
    return recordPids.findOrAddAllDiscoveredPidsForResource(pids.values());
  }
}
