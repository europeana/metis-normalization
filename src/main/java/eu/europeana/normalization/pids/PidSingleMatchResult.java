package eu.europeana.normalization.pids;

/**
 * A single match of a PID against a PID scheme in the vocabulary.
 *
 * @param scheme        The PID scheme that matched the PID. Is not null.
 * @param canonicalPid  The canonical version of the PID. Is not null.
 * @param resolvablePid The resolvable version of the PID. Is not null.
 * @param originalPid   The original (provided) version of the PID. Is not null.
 * @param start         The first character (inclusive) in the input where the original PID was found.
 * @param end           The last character (exclusive) in the input where the original PID was found.
 */
public record PidSingleMatchResult(PidSchemeInfo scheme, String canonicalPid, String resolvablePid,
                                   String originalPid, int start, int end) {

}
