package eu.europeana.normalization.dates.sanitize;

/**
 * Class containing the sanitize operation that was used to sanitize a value and the sanitized value itself.
 */
public record SanitizedDate(SanitizeOperation sanitizeOperation, String sanitizedDateString) {

}
