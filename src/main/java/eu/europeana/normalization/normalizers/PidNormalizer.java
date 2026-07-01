package eu.europeana.normalization.normalizers;

import eu.europeana.metis.schema.jibx.AboutType;
import eu.europeana.metis.schema.jibx.Aggregation;
import eu.europeana.metis.schema.jibx.EuropeanaType;
import eu.europeana.metis.schema.jibx.EuropeanaType.Choice;
import eu.europeana.metis.schema.jibx.HasMimeType;
import eu.europeana.metis.schema.jibx.Identifier;
import eu.europeana.metis.schema.jibx.LiteralType;
import eu.europeana.metis.schema.jibx.Pid;
import eu.europeana.metis.schema.jibx.ProxyType;
import eu.europeana.metis.schema.jibx.RDF;
import eu.europeana.metis.schema.jibx.ResourceOrLiteralType.Resource;
import eu.europeana.metis.schema.jibx.ResourceType;
import eu.europeana.metis.schema.jibx.WebResourceType;
import eu.europeana.normalization.model.ConfidenceLevel;
import eu.europeana.normalization.model.NormalizeActionResult;
import eu.europeana.normalization.model.RecordWrapper;
import eu.europeana.normalization.pids.NormalizedPidsForRecord;
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
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

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
    final NormalizedPidsForRecord normalizedPids = new NormalizedPidsForRecord(rdf);

    // Normalize PIDs.
    normalizePids(rdf, normalizedPids, report);

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
   * Collect a list of resources into a map with the about values as key.
   *
   * @param resources The list to convert.
   * @param <T>       The type of the resource objects.
   * @return The map.
   */
  private static <T extends AboutType> Map<String, T> toMap(List<T> resources) {
    final Map<String, T> result = new HashMap<>();
    Optional.ofNullable(resources).stream().flatMap(Collection::stream)
        .filter(Objects::nonNull).filter(resource -> resource.getAbout() != null)
        .forEach(resource -> result.put(resource.getAbout(), resource));
    return result;
  }

  /**
   * This method returns all web resource references to media links from the aggregations. More
   * precisely: it returns each isShownBy and hasView reference for which we cannot prove that it is
   * not a media link. A media link is defined as a link to content that is not HTML.
   *
   * @param aggregations   The aggregations from which to obtain the references. Is not
   *                       <code>null</code>.
   * @param webResourceMap The web resources by their about value. Is not <code>null</code>. This
   *                       list is used to determine whether the resource might be an HTML
   *                       resource.
   * @return The set of resource references (can be empty).
   */
  private Set<String> getMediaReferences(Collection<Aggregation> aggregations,
      Map<String, WebResourceType> webResourceMap) {

    // Collect all isShownBy and isShownAt references.
    final Stream<ResourceType> isShownByStream = aggregations.stream()
        .map(Aggregation::getIsShownBy);
    final Stream<ResourceType> hasViewStream = aggregations.stream()
        .map(Aggregation::getHasViewList).filter(Objects::nonNull).flatMap(Collection::stream);
    final Set<String> result = new HashSet<>();
    Stream.concat(isShownByStream, hasViewStream).filter(Objects::nonNull)
        .map(ResourceType::getResource).filter(Objects::nonNull).forEach(result::add);

    // Remove those that represent HTML resources.
    result.stream().map(webResourceMap::get).filter(Objects::nonNull)
        .filter(webResource -> {
          final String contentType = Optional.ofNullable(webResource.getHasMimeType())
              .map(HasMimeType::getHasMimeType).orElse(null);
          return "text/html".equals(contentType) || "application/xhtml+xml".equals(contentType);
        })
        .map(AboutType::getAbout).forEach(result::remove);

    // Return the result.
    return result;
  }

  private void normalizePids(RDF rdfRecord, NormalizedPidsForRecord normalizedPids,
      InternalNormalizationReport report) {

    // Collect some objects in maps.
    final Map<String, WebResourceType> webResourceMap = toMap(rdfRecord.getWebResourceList());
    final Map<String, Aggregation> aggregationMap = toMap(rdfRecord.getAggregationList());

    // Perform PID normalization for proxy objects.
    final Stream<ProxyType> proxyStream = Optional.ofNullable(rdfRecord.getProxyList()).stream()
        .flatMap(Collection::stream).filter(Objects::nonNull);
    proxyStream.forEach(proxy -> {

      // Compute the aggregations associated with this proxy.
      final List<Aggregation> proxyAggregations = Optional
          .ofNullable(proxy.getProxyInList()).stream().flatMap(Collection::stream)
          .filter(Objects::nonNull).map(ResourceType::getResource).filter(Objects::nonNull)
          .map(aggregationMap::get).filter(Objects::nonNull).toList();

      // Compute the potential PID references from other (non-PID) fields.
      final Set<String> potentialPidReferences = new HashSet<>();
      proxyAggregations.stream().map(Aggregation::getIsShownAt).filter(Objects::nonNull)
          .map(ResourceType::getResource).filter(Objects::nonNull)
          .forEach(potentialPidReferences::add);
      potentialPidReferences.addAll(getMediaReferences(proxyAggregations, webResourceMap));
      Optional.ofNullable(proxy.getChoiceList()).stream()
          .flatMap(Collection::stream).filter(Objects::nonNull)
          .filter(Choice::ifIdentifier).map(Choice::getIdentifier).filter(Objects::nonNull)
          .map(LiteralType::getString).filter(Objects::nonNull)
          .forEach(potentialPidReferences::add);

      // Normalize the PIDs in this proxy.
      final List<Pid> allPidsInProxy = Optional.ofNullable(proxy.getPidList()).stream()
          .flatMap(Collection::stream).filter(Objects::nonNull).toList();
      final List<Pid> updatedList = normalizePidsForResource(allPidsInProxy,
          potentialPidReferences, normalizedPids, report);
      proxy.setPidList(updatedList);
    });
  }

  /**
   * Normalize the PID values in accordance with the known PID schemes.
   *
   * @param allPidsInResource      The PIDs that we find in the resource (before normalization).
   * @param potentialPidReferences Any other references in the resource that may contain PIDs.
   * @param normalizedPids         The collection of known PID objects in the record. New PID
   *                               objects created during this operation must be added here.
   * @param report                 The report in which to tally operations.
   * @return The PIDs that we should have in the resource (after normalization).
   */
  private List<Pid> normalizePidsForResource(List<Pid> allPidsInResource,
      Set<String> potentialPidReferences, NormalizedPidsForRecord normalizedPids,
      InternalNormalizationReport report) {

    // Set up some collections: split into PIDs that need normalization and those that don't.
    final List<Pid> nonNormalizedPids = allPidsInResource.stream()
        .filter(pid -> StringUtils.isNotBlank(pid.getString())).toList();
    final Set<String> normalizedPidIds = allPidsInResource.stream()
        .map(Pid::getResource).filter(Objects::nonNull).map(Resource::getResource)
        .filter(StringUtils::isNotBlank).collect(Collectors.toSet());

    // Normalize PIDs: go by all unnormalized PIDs and all potential PID references.
    final List<Pid> resultPids = new ArrayList<>();
    final Stream<Pair<String, Pid>> pidsToNormalize = Stream.concat(
        nonNormalizedPids.stream().map(pid -> new ImmutablePair<>(pid.getString(), pid)),
        potentialPidReferences.stream().map(ref -> new ImmutablePair<>(ref, null)));
    pidsToNormalize.forEach(referencePidPair -> {

      // Attempt normalization.
      final PidMultipleMatchResult normalization = pidSchemeVocabulary.matchPid(
          referencePidPair.getLeft());
      if (normalization == null) {

        // Add any non-normalized PID directly to the result PIDs.
        Optional.ofNullable(referencePidPair.getRight()).ifPresent(resultPids::add);
      } else {

        // Find/create the normalized PID object. Collect IDs in a set to guarantee uniqueness.
        normalizedPidIds.add(normalizedPids.findOrAddNormalizedPid(normalization));

        // Add to the report
        report.increment(this.getClass().getSimpleName(), ConfidenceLevel.CERTAIN);
      }
    });

    // Add the normalized PID IDs to the result list as PID references.
    for (String id : normalizedPidIds) {
      final Pid newPid = new Pid();
      newPid.setResource(new Resource());
      newPid.getResource().setResource(id);
      newPid.setString("");
      resultPids.add(newPid);
    }

    // Done
    return resultPids;
  }
}
