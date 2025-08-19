package eu.europeana.normalization.model;

/**
 * The result of a normalize action.
 *
 * @param edmRecord The record after normalization.
 * @param report The report on the normalization action.
 */
public record NormalizeActionResult(RecordWrapper edmRecord, NormalizationReport report) {

}
