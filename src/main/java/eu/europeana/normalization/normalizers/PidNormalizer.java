package eu.europeana.normalization.normalizers;

import eu.europeana.metis.schema.jibx.Pid;
import eu.europeana.metis.schema.jibx.ProxyType;
import eu.europeana.metis.schema.jibx.RDF;
import eu.europeana.metis.schema.jibx.ResourceOrLiteralType.Resource;
import eu.europeana.normalization.model.ConfidenceLevel;
import eu.europeana.normalization.model.NormalizeActionResult;
import eu.europeana.normalization.model.RecordWrapper;
import eu.europeana.normalization.pids.NormalizedPids;
import eu.europeana.normalization.pids.PidMatchResult;
import eu.europeana.normalization.pids.PidSchemeVocabularyCached;
import eu.europeana.normalization.util.NormalizationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
  public PidNormalizer(PidSchemeVocabularyCached pidSchemeVocabularyCached)  {
    this.pidSchemeVocabulary = pidSchemeVocabularyCached;
  }

  @Override
  public NormalizeActionResult normalize(RecordWrapper edmRecord) throws NormalizationException {

    // Get all data out of the rdf.
    final RDF rdf = edmRecord.getAsRDF();
    final NormalizedPids normalizedPids = new NormalizedPids(rdf);

    // Go by each proxy.
    final List<ProxyType> proxies = Optional.ofNullable(rdf.getProxyList()).stream()
        .flatMap(Collection::stream).toList();
    final InternalNormalizationReport report = new InternalNormalizationReport();
    for (ProxyType proxy : proxies) {
      normalizePidsInProxy(proxy, normalizedPids, report);
    }

    // Override all the normalized PIDs and PID schemes in the record as new ones were added.
    normalizedPids.writeToRecord(rdf);

    // Done
    return new NormalizeActionResult(RecordWrapper.create(rdf), report);
  }

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
      final PidMatchResult normalization = pidSchemeVocabulary.matchPid(nonNormalizedPid.getString());
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
