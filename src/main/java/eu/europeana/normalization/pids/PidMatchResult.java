package eu.europeana.normalization.pids;

/**
 * A match of a PID against the vocabulary.
 *
 * @param scheme        The PID scheme that matched the PID. Is not null.
 * @param canonicalPid  The canonical version of the PID. Is not null.
 * @param resolvablePid The resolvable version of the PID. Is not null.
 * @param originalPid   The original version of the PID. Is not null.
 */
public record PidMatchResult(PidSchemeInfo scheme, String canonicalPid, String resolvablePid,
                             String originalPid) {

}
