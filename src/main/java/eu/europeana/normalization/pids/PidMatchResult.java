package eu.europeana.normalization.pids;

/**
 * A match of a PID against the vocabulary.
 *
 * @param scheme        The PID scheme that matched the PID.
 * @param canonicalPid  The canonical version of the PID.
 * @param resolvablePid The resolvable version of the PID.
 * @param originalPid   The original version of the PID.
 */
public record PidMatchResult(PidSchemeInfo scheme, String canonicalPid, String resolvablePid,
                             String originalPid) {

}
