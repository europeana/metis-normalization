package eu.europeana.normalization.normalizers;

import eu.europeana.metis.schema.jibx.AboutType;
import eu.europeana.metis.schema.jibx.HasURL;
import eu.europeana.metis.schema.jibx.InScheme;
import eu.europeana.metis.schema.jibx.LiteralType;
import eu.europeana.metis.schema.jibx.Notation;
import eu.europeana.metis.schema.jibx.PersistentIdentifierType;
import eu.europeana.metis.schema.jibx.Pid;
import eu.europeana.metis.schema.jibx.ProxyType;
import eu.europeana.metis.schema.jibx.RDF;
import eu.europeana.metis.schema.jibx.ResourceOrLiteralType.Resource;
import eu.europeana.metis.schema.jibx.Value;
import eu.europeana.normalization.model.ConfidenceLevel;
import eu.europeana.normalization.model.NormalizeActionResult;
import eu.europeana.normalization.model.RecordWrapper;
import eu.europeana.normalization.pids.PidMatchResult;
import eu.europeana.normalization.pids.PidSchemeVocabulary;
import eu.europeana.normalization.util.NormalizationConfigurationException;
import eu.europeana.normalization.util.NormalizationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * This is a normalizer for PID values.
 */
public class PidNormalizer implements RecordNormalizeAction {

  private final Function<String, PidMatchResult> pidSchemeMatcher;

  /**
   * Constructor.
   * @throws NormalizationConfigurationException If the vocabulary could not be loaded.
   */
  public PidNormalizer() throws NormalizationConfigurationException {
    this.pidSchemeMatcher = PidSchemeVocabulary.getMatcher();
  }

  @Override
  public NormalizeActionResult normalize(RecordWrapper edmRecord) throws NormalizationException {

    // Get all data out of the rdf.
    final RDF rdf = edmRecord.getAsRDF();
    final Map<String, PersistentIdentifierType> normalizedPidsById = Optional
        .ofNullable(rdf.getPersistentIdentifierList()).stream().flatMap(Collection::stream)
        .collect(Collectors.toMap(AboutType::getAbout, Function.identity()));

    // Go by each proxy.
    final List<ProxyType> proxies = Optional.ofNullable(rdf.getProxyList()).stream()
        .flatMap(Collection::stream).toList();
    final InternalNormalizationReport report = new InternalNormalizationReport();
    for (ProxyType proxy : proxies) {

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
        final PidMatchResult normalization = pidSchemeMatcher.apply(nonNormalizedPid.getString());
        if (normalization == null) {
          resultPidsInProxy.add(nonNormalizedPid);
          continue;
        }

        // Find or create the normalized PID object. Collect IDs in a set to guarantee uniqueness.
        final PersistentIdentifierType normalizedPid = findOrAddNormalizedPidIfNeeded(
            normalization, normalizedPidsById);
        normalizedPidIdsInProxy.add(normalizedPid.getAbout());

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

    // Override all the normalized PIDs and PID schemes in the record as new ones were added.
    rdf.setPersistentIdentifierList(new ArrayList<>(normalizedPidsById.values()));

    // Done
    return new NormalizeActionResult(RecordWrapper.create(rdf), report);
  }

  private static PersistentIdentifierType findOrAddNormalizedPidIfNeeded(
      PidMatchResult normalization, Map<String, PersistentIdentifierType> normalizedPidsById) {

    // Try to find a matching PID object.
    final Optional<PersistentIdentifierType> existingPid = normalizedPidsById.values().stream()
        .filter(candidate -> candidate.getValue().getString().equals(normalization.canonicalPid()))
        .findAny();

    // If there is no matching PID object, create a new one and add it to the map.
    final PersistentIdentifierType pid = existingPid.orElseGet(() -> {
      final PersistentIdentifierType result = new PersistentIdentifierType();
      result.setAbout(computeNextPidAbout(normalizedPidsById.keySet()));
      result.setValue(new Value());
      result.getValue().setString(normalization.canonicalPid());
      result.setHasURLList(List.of(new HasURL()));
      result.getHasURLList().getFirst().setResource(normalization.resolvablePid());
      result.setInScheme(new InScheme());
      result.getInScheme().setResource(normalization.scheme().getSchemeId());
      normalizedPidsById.put(result.getAbout(), result);
      return result;
    });

    // Add the original, unnormalized pid as a notation if different from the canonical or
    // resolvable one (and if it doesn't already exist as a notation).
    final boolean addOriginalPidAsNotation =
        !normalization.originalPid().equals(normalization.canonicalPid()) &&
            !normalization.originalPid().equals(normalization.resolvablePid()) &&
            Optional.ofNullable(pid.getNotationList()).stream().flatMap(Collection::stream)
                .map(LiteralType::getString).noneMatch(normalization.originalPid()::equals);
    if (addOriginalPidAsNotation) {
      if (pid.getNotationList() == null) {
        pid.setNotationList(new ArrayList<>());
      }
      final Notation notation = new Notation();
      notation.setString(normalization.originalPid());
      pid.getNotationList().add(notation);
    }

    // Done.
    return pid;
  }

  private static String computeNextPidAbout(Set<String> normalizedPidIds) {
    for (int i = 0; ; i++) {
      final String proposedId = "#pid_" + i;
      if (!normalizedPidIds.contains(proposedId)) {
        return proposedId;
      }
    }
  }
}
