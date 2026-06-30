package eu.europeana.normalization.normalizers;

import eu.europeana.metis.schema.jibx.Aggregation;
import eu.europeana.metis.schema.jibx.EuropeanaType;
import eu.europeana.metis.schema.jibx.EuropeanaType.Choice;
import eu.europeana.metis.schema.jibx.Identifier;
import eu.europeana.metis.schema.jibx.LiteralType;
import eu.europeana.metis.schema.jibx.Pid;
import eu.europeana.metis.schema.jibx.ProxyType;
import eu.europeana.metis.schema.jibx.RDF;
import eu.europeana.metis.schema.jibx.ResourceOrLiteralType.Resource;
import eu.europeana.metis.schema.jibx.ResourceType;
import eu.europeana.normalization.model.ConfidenceLevel;
import eu.europeana.normalization.model.NormalizeActionResult;
import eu.europeana.normalization.model.RecordWrapper;
import eu.europeana.normalization.pids.NormalizedPids;
import eu.europeana.normalization.pids.PidCorrectionVocabulary;
import eu.europeana.normalization.pids.PidMultipleMatchResult;
import eu.europeana.normalization.pids.PidSchemeVocabularyCached;
import eu.europeana.normalization.util.NormalizationConfigurationException;
import eu.europeana.normalization.util.NormalizationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * This is a normalizer for PID values.
 */
public class PidNormalizer implements RecordNormalizeAction {

  private final PidSchemeVocabularyCached pidSchemeVocabulary;

  /**
   * Instantiates a new Pid normalizer.
   *
   * @param pidSchemeVocabularyCached the pid scheme vocabulary cached
   */
  public PidNormalizer(PidSchemeVocabularyCached pidSchemeVocabularyCached) {
    this.pidSchemeVocabulary = pidSchemeVocabularyCached;
  }

  @Override
  public NormalizeActionResult normalize(RecordWrapper edmRecord) throws NormalizationException {

    // Set up the normalization
    final RDF rdf = edmRecord.getAsRDF();
    final InternalNormalizationReport report = new InternalNormalizationReport();

    // Remove dc:identifier values that also occur as media references.
    removeUnneededDcIdentifiers(rdf, report);

    // Correct dc:identifier values. Note: this is after removing duplicates - it is more likely
    // that any duplicate values occur in the same form, so correcting first yields fewer matches.
    correctDcIdentifiers(rdf, report);

    // Set up the collection of normalized PIDs.
    final NormalizedPids normalizedPids = new NormalizedPids(rdf);

    // Go by each proxy.
    Optional.ofNullable(rdf.getProxyList()).stream().flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .forEach(proxy -> normalizePidsInProxy(proxy, normalizedPids, report));

    // Override all the normalized PIDs and PID schemes in the record as new ones were added.
    normalizedPids.writeToRecord(rdf);

    // Done
    return new NormalizeActionResult(RecordWrapper.create(rdf), report);
  }

  /**
   * Extracts the media links from the aggregation object.
   *
   * @param aggregation The aggregation object.
   * @return The set of media links.
   */
  private static Set<String> extractMediaReferences(Aggregation aggregation) {
    final Set<String> result = new HashSet<>();
    Optional.ofNullable(aggregation.getIsShownBy()).map(ResourceType::getResource)
        .ifPresent(result::add);
    Optional.ofNullable(aggregation.getObject()).map(ResourceType::getResource)
        .ifPresent(result::add);
    Optional.ofNullable(aggregation.getHasViewList()).stream().flatMap(Collection::stream)
        .filter(Objects::nonNull).map(ResourceType::getResource)
        .filter(Objects::nonNull).forEach(result::add);
    return result;
  }

  /**
   * Checks the media references to see whether the given identifier from the given proxy also
   * occurs as media reference for that proxy (i.e., in one of the aggregations associated
   * with the proxy).
   *
   * @param mediaReferencesByAggregation The media references for the entire record.
   * @param proxy                        The proxy object from which the identifier is taken.
   * @param identifier                   The identifier to check.
   * @return Whether the given identifier from the given proxy occurs as a media reference for that
   * proxy.
   */
  private static boolean hasMediaReferenceForProxy(
      Map<String, Set<String>> mediaReferencesByAggregation, ProxyType proxy,
      Identifier identifier) {
    final Predicate<String> mediaReferenceExists = reference -> Optional
        .ofNullable(proxy.getProxyInList()).stream().flatMap(Collection::stream)
        .filter(Objects::nonNull).map(ResourceType::getResource).filter(Objects::nonNull)
        .map(mediaReferencesByAggregation::get).filter(Objects::nonNull)
        .anyMatch(references -> references.contains(reference));
    return Optional.ofNullable(identifier).map(LiteralType::getString)
        .map(mediaReferenceExists::test).orElse(false);
  }

  /**
   * Remove all dc:identifier values that also occur as media references for its proxy (i.e., in one
   * of the aggregations associated with its proxy).
   *
   * @param rdf    The record.
   * @param report The report in which to tally operations.
   */
  private void removeUnneededDcIdentifiers(RDF rdf, InternalNormalizationReport report) {

    // Get the media references by aggregation ID. No null values.
    final Map<String, Set<String>> mediaReferencesByAggregation = new HashMap<>();
    Optional.ofNullable(rdf.getAggregationList()).stream().flatMap(Collection::stream).forEach(
        aggregation -> mediaReferencesByAggregation.computeIfAbsent(aggregation.getAbout(),
            about -> new HashSet<>()).addAll(extractMediaReferences(aggregation)));

    // Remove dc:identifier values that also occur as media reference.
    Optional.ofNullable(rdf.getProxyList()).stream().flatMap(Collection::stream).forEach(proxy ->
        proxy.setChoiceList(Optional.ofNullable(proxy.getChoiceList()).stream()
            .flatMap(Collection::stream).filter(Objects::nonNull)
            .filter(choice -> {
              final boolean removeChoice = choice.ifIdentifier() && hasMediaReferenceForProxy(
                  mediaReferencesByAggregation, proxy, choice.getIdentifier());
              if (removeChoice) {
                report.increment(this.getClass().getSimpleName(), ConfidenceLevel.CERTAIN);
              }
              return !removeChoice;
            }).toList())
    );
  }

  /**
   * Correct dc:identifier values according to the vocabulary of PID corrections.
   *
   * @param rdf    The record.
   * @param report The report in which to tally operations.
   * @throws NormalizationException If an issue occurs while attempting to load the corrections.
   */
  private void correctDcIdentifiers(RDF rdf, InternalNormalizationReport report)
      throws NormalizationException {

    // Load the corrections (if not already loaded).
    final PidCorrectionVocabulary vocabulary;
    try {
      vocabulary = PidCorrectionVocabulary.getInstance();
    } catch (NormalizationConfigurationException e) {
      throw new NormalizationException("Could not load the corrections.", e);
    }

    // Apply correction to all dc:identifier values.
    Optional.ofNullable(rdf.getProxyList()).stream().flatMap(Collection::stream)
        .filter(Objects::nonNull).map(EuropeanaType::getChoiceList).filter(Objects::nonNull)
        .flatMap(Collection::stream).filter(Objects::nonNull)
        .filter(Choice::ifIdentifier).map(Choice::getIdentifier).filter(Objects::nonNull)
        .forEach(dcIdentifier -> {
          final String original = dcIdentifier.getString();
          Optional.ofNullable(original).map(vocabulary::attemptCorrection)
              .ifPresent(dcIdentifier::setString);
          if (!Objects.equals(original, dcIdentifier.getString())) {
            report.increment(this.getClass().getSimpleName(), ConfidenceLevel.CERTAIN);
          }
        });
  }

  /**
   * Normalize the PID values in accordance with the known PID schemes.
   *
   * @param proxy          The proxy in which to look for PID values.
   * @param normalizedPids The collection of known PID objects in the record. New PID objects
   *                       created during this operation must be added here.
   * @param report         The report in which to tally operations.
   */
  private void normalizePidsInProxy(ProxyType proxy, NormalizedPids normalizedPids,
      InternalNormalizationReport report) {

    // Set up some collections: split into PIDs that need normalization and those that don't.
    final List<Pid> allPidsInProxy = Optional.ofNullable(proxy.getPidList()).stream()
        .flatMap(Collection::stream).filter(Objects::nonNull).toList();
    final List<Pid> nonNormalizedPidsInProxy = allPidsInProxy.stream()
        .filter(pid -> !StringUtils.isBlank(pid.getString())).toList();
    final Set<String> normalizedPidIdsInProxy = allPidsInProxy.stream()
        .map(Pid::getResource).filter(Objects::nonNull).map(Resource::getResource)
        .filter(resource -> !StringUtils.isBlank(resource)).collect(Collectors.toSet());

    // Normalize PIDs.
    final List<Pid> resultPidsInProxy = new ArrayList<>();
    for (Pid nonNormalizedPid : nonNormalizedPidsInProxy) {

      // Normalize the PID. If we can't, add the PID directly as a result.
      final PidMultipleMatchResult normalization = pidSchemeVocabulary.matchPid(
          nonNormalizedPid.getString());
      if (normalization == null) {
        resultPidsInProxy.add(nonNormalizedPid);
        continue;
      }

      // Find or create the normalized PID object. Collect IDs in a set to guarantee uniqueness.
      normalizedPidIdsInProxy.add(normalizedPids.findOrAddNormalizedPid(normalization));

      // Add to the report
      report.increment(this.getClass().getSimpleName(), ConfidenceLevel.CERTAIN);
    }

    // Add the normalized PID IDs to the result list and replace the list in the proxy.
    for (String id : normalizedPidIdsInProxy) {
      final Pid newPid = new Pid();
      newPid.setResource(new Resource());
      newPid.getResource().setResource(id);
      newPid.setString("");
      resultPidsInProxy.add(newPid);
    }
    proxy.setPidList(resultPidsInProxy);
  }
}
