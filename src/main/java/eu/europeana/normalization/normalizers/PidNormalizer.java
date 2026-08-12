package eu.europeana.normalization.normalizers;

import eu.europeana.metis.schema.jibx.AboutType;
import eu.europeana.metis.schema.jibx.Aggregation;
import eu.europeana.metis.schema.jibx.EuropeanaProxy;
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
import eu.europeana.normalization.pids.DiscoveredPidsForResource;
import eu.europeana.normalization.pids.NormalizedPidsForRecord;
import eu.europeana.normalization.pids.PidCorrectionVocabulary;
import eu.europeana.normalization.pids.PidMultipleMatchResult;
import eu.europeana.normalization.pids.PidSchemeVocabularyCached;
import eu.europeana.normalization.pids.PidSingleMatchResult;
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
import org.apache.commons.lang3.stream.Streams;

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

    // Normalize PIDs.
    normalizePids(rdf, report);

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
    Streams.nonNull(aggregation.getHasViewList()).map(ResourceType::getResource)
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
    final Predicate<String> mediaReferenceExists = reference ->
        Streams.nonNull(proxy.getProxyInList())
            .map(ResourceType::getResource).filter(Objects::nonNull)
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
    Streams.nonNull(rdf.getAggregationList()).forEach(
        aggregation -> mediaReferencesByAggregation.computeIfAbsent(aggregation.getAbout(),
            about -> new HashSet<>()).addAll(extractMediaReferences(aggregation)));

    // Remove dc:identifier values that also occur as media reference.
    Streams.nonNull(rdf.getProxyList()).forEach(proxy -> {
      final List<Choice> oldChoices = Streams.nonNull(proxy.getChoiceList()).toList();
      final List<Choice> newChoices = oldChoices.stream().filter(choice ->
          !choice.ifIdentifier() || !hasMediaReferenceForProxy(
              mediaReferencesByAggregation, proxy, choice.getIdentifier())).toList();
      report.multipleIncrement(this.getClass().getSimpleName(), ConfidenceLevel.CERTAIN,
          oldChoices.size() - newChoices.size());
      proxy.setChoiceList(newChoices);
    });
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
    Streams.nonNull(rdf.getProxyList()).map(EuropeanaType::getChoiceList).filter(Objects::nonNull)
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
    Streams.nonNull(resources).filter(resource -> resource.getAbout() != null)
        .forEach(resource -> result.put(resource.getAbout(), resource));
    return result;
  }

  /**
   * Collect a list of isShownBy and hasView references. Note: we don't check whether there is an
   * associated web resource.
   *
   * @param aggregations The aggregations from which to obtain the references. Is not
   *                     <code>null</code>.
   * @return The set of resource references (can be empty).
   */
  private Set<String> getIsShownByAndHasViewReferences(Collection<Aggregation> aggregations) {
    final Stream<ResourceType> isShownByStream = aggregations.stream()
        .map(Aggregation::getIsShownBy);
    final Stream<ResourceType> hasViewStream = aggregations.stream()
        .map(Aggregation::getHasViewList).filter(Objects::nonNull).flatMap(Collection::stream);
    final Set<String> result = new HashSet<>();
    Stream.concat(isShownByStream, hasViewStream).filter(Objects::nonNull)
        .map(ResourceType::getResource).filter(Objects::nonNull).forEach(result::add);
    return result;
  }

  /**
   * Determines whether the given content type is a website type (instead of a media type).
   *
   * @param contentType A valid content type (mime type). Can be <code>null</code>, in which case
   *                    <code>false</code> will be returned.
   * @return Whether the content type can be proven to be a website.
   */
  private static boolean contentTypeIsWebsite(String contentType) {
    return "text/html".equals(contentType) || "application/xhtml+xml".equals(contentType);
  }

  /**
   * This method returns all web resource references to non-media links from the aggregations. More
   * precisely: it returns each isShownBy and hasView reference for which we can prove that it
   * is a website link (HTML/XHMTL). If we don't have a content type, we assume it is not a website
   * link, as isShownBy and hasView are not supposed to be. Additionally, it returns all isShownAt
   * references, as they are always supposed to be non-media links.
   *
   * @param aggregations   The aggregations from which to obtain the references. Is not
   *                       <code>null</code>.
   * @param webResourceMap The web resources by their about value. Is not <code>null</code>. This
   *                       list is used to determine whether the resource might be an HTML
   *                       resource.
   * @return The set of resource references (can be empty).
   */
  private Set<String> getNonMediaWebReferences(Collection<Aggregation> aggregations,
      Map<String, WebResourceType> webResourceMap) {

    // Collect all isShownBy and hasView references that are proven to represent non-media resources
    // (i.e., HTML/XHTML resources). Ensure a mutable hashset is created, so we can add it later.
    final Set<String> result = getIsShownByAndHasViewReferences(aggregations).stream()
        .filter(webResourceId -> {
          final String contentType = Optional.ofNullable(webResourceMap.get(webResourceId))
              .map(WebResourceType::getHasMimeType).map(HasMimeType::getHasMimeType).orElse(null);
          return contentTypeIsWebsite(contentType);
        }).collect(Collectors.toCollection(HashSet::new));

    // Add all isShownAt references (they should all represent non-media resources).
    aggregations.stream().map(Aggregation::getIsShownAt).filter(Objects::nonNull)
        .map(ResourceType::getResource).filter(Objects::nonNull).forEach(result::add);

    // Return the result.
    return result;
  }

  /**
   * Extract potential PID references from the record (i.e., the various proxies and their
   * associated aggregations and web resources). The result can be used for PID discovery.
   *
   * @param proxies        The list of proxies in the record.
   * @param webResourceMap The web resources by their about value. Is not <code>null</code>.
   * @param aggregationMap The aggregations by their about value. Is not <code>null</code>.
   * @return The set of potential PID references.
   */
  private Set<String> extractPotentialPidReferencesFromProxies(List<ProxyType> proxies,
      Map<String, WebResourceType> webResourceMap, Map<String, Aggregation> aggregationMap) {
    final Set<String> potentialPidReferences = new HashSet<>();
    proxies.forEach(proxy -> {

      // Compute the aggregations associated with this proxy.
      final List<Aggregation> proxyAggregations = Streams.nonNull(proxy.getProxyInList())
          .map(ResourceType::getResource).filter(Objects::nonNull).distinct()
          .map(aggregationMap::get).filter(Objects::nonNull).toList();

      // Compute the potential PID references from other (non-PID) fields.
      potentialPidReferences.addAll(getNonMediaWebReferences(proxyAggregations, webResourceMap));
      Streams.nonNull(proxy.getChoiceList()).filter(Choice::ifIdentifier)
          .map(Choice::getIdentifier).filter(Objects::nonNull)
          .map(LiteralType::getString).filter(Objects::nonNull)
          .forEach(potentialPidReferences::add);
    });
    return potentialPidReferences;
  }

  /**
   * Normalize the record for PIDs.
   *
   * @param rdfRecord The record to normalize.
   * @param report    The report in which to tally operations.
   */
  private void normalizePids(RDF rdfRecord, InternalNormalizationReport report) {

    // Collect some objects in lists and maps.
    final NormalizedPidsForRecord normalizedPids = new NormalizedPidsForRecord(rdfRecord);
    final Map<String, WebResourceType> webResourceMap = toMap(rdfRecord.getWebResourceList());
    final Map<String, Aggregation> aggregationMap = toMap(rdfRecord.getAggregationList());
    final List<ProxyType> proxies = Streams.nonNull(rdfRecord.getProxyList()).toList();

    // Perform PID normalization for proxy objects (as individual resources). Sets all PID lists.
    proxies.forEach(proxy -> {
      final List<Pid> allPidsInProxy = Streams.nonNull(proxy.getPidList()).toList();
      proxy.setPidList(normalizePidsForResource(allPidsInProxy, normalizedPids, report));
    });

    // If there are no PIDs in any proxy, we do discovery of PIDs for this record (as a resource).
    // If we find any, they are added to the Europeana proxy.
    if (proxies.stream().map(ProxyType::getPidList).allMatch(List::isEmpty)) {
      final Set<String> potentialPids = extractPotentialPidReferencesFromProxies(proxies,
          webResourceMap, aggregationMap);
      final List<Pid> newPids = discoverPidsForResource(potentialPids, normalizedPids, report);
      proxies.stream().filter(proxy -> Optional.ofNullable(proxy.getEuropeanaProxy())
              .map(EuropeanaProxy::isEuropeanaProxy).orElse(false))
          .findAny().ifPresent(proxy -> proxy.setPidList(newPids));
    }

    // Perform PID normalization for web resource objects. Set all PID lists.
    webResourceMap.values().forEach(webResource -> {
      final List<Pid> pids = Streams.nonNull(webResource.getPidList()).toList();
      webResource.setPidList(normalizePidsForResource(pids, normalizedPids, report));
    });

    // Do discovery on web resources for which no actual PIDs are found and that are or should be
    // media references (so isShownBy and hasView references that are not proven to be websites).
    getIsShownByAndHasViewReferences(aggregationMap.values()).stream()
        .map(webResourceMap::get).filter(Objects::nonNull)
        .filter(webResource -> webResource.getPidList().isEmpty())
        .filter(webResource -> {
          final String contentType = Optional.ofNullable(webResource.getHasMimeType())
              .map(HasMimeType::getHasMimeType).orElse(null);
          return !contentTypeIsWebsite(contentType);
        })
        .forEach(webResource -> webResource.setPidList(
            discoverPidsForResource(Set.of(webResource.getAbout()), normalizedPids, report)));

    // Override all the normalized PIDs and PID schemes in the record as new ones were added.
    normalizedPids.writeToRecord(rdfRecord);
  }

  /**
   * Normalize the PID values in accordance with the known PID schemes.
   *
   * @param allPidsInResource      The PIDs that we find in the resource (before normalization).
   * @param recordPids             The collection of known PID objects in the record. New PID
   *                               objects created during this operation must be added here.
   * @param report                 The report in which to tally operations.
   * @return The PIDs that we should have in the resource (after normalization).
   */
  private List<Pid> normalizePidsForResource(List<Pid> allPidsInResource,
      NormalizedPidsForRecord recordPids, InternalNormalizationReport report) {

    // Get the PID references and literals.
    final Set<String> pidReferences = allPidsInResource.stream()
        .map(Pid::getResource).filter(Objects::nonNull)
        .map(Resource::getResource).filter(StringUtils::isNotBlank)
        .collect(Collectors.toSet());
    final Set<String> pidLiterals = allPidsInResource.stream()
        .map(Pid::getString).filter(Objects::nonNull).collect(Collectors.toSet());

    // Try to match the literals, incrementing the report for every success.
    final List<PidSingleMatchResult> literalMatches = pidLiterals.stream()
        .map(pidSchemeVocabulary::matchPid).filter(Objects::nonNull).toList();
    report.multipleIncrement(this.getClass().getSimpleName(), ConfidenceLevel.CERTAIN,
        literalMatches.size());

    // Find or create PID objects and return the successful references.
    final Set<String> allReferencesFromPids = recordPids.findOrAddAllProvidedPidsForResource(
        pidReferences, literalMatches);
    return new ArrayList<>(createPids(allReferencesFromPids));
  }

  /**
   * Discover the PID values in other fields and normalize in accordance with the known PID schemes.
   *
   * @param potentialPids Any other values in the resource that may contain PIDs.
   * @param recordPids    The collection of known PID objects in the record. New PID objects created
   *                      during this operation must be added here.
   * @param report        The report in which to tally operations.
   * @return The PIDs that we should have in the resource (after normalization).
   */
  private List<Pid> discoverPidsForResource(Set<String> potentialPids,
      NormalizedPidsForRecord recordPids, InternalNormalizationReport report) {
    final DiscoveredPidsForResource discoveredPidCollection = new DiscoveredPidsForResource();
    final List<PidMultipleMatchResult> discoveredPids = potentialPids.stream()
        .map(pidSchemeVocabulary::findPids).filter(Objects::nonNull).toList();
    report.multipleIncrement(this.getClass().getSimpleName(), ConfidenceLevel.CERTAIN,
        discoveredPids.size());
    discoveredPids.forEach(discoveredPidCollection::addPid);
    return createPids(discoveredPidCollection.writeToRecord(recordPids));
  }

  private static List<Pid> createPids(Set<String> references) {
    return references.stream().map(reference -> {
      final Pid newPid = new Pid();
      newPid.setResource(new Resource());
      newPid.getResource().setResource(reference);
      newPid.setString("");
      return newPid;
    }).toList();
  }
}
