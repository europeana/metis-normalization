package eu.europeana.normalization.pids;

import eu.europeana.metis.schema.jibx.AboutType;
import eu.europeana.metis.schema.jibx.EquivalentPID;
import eu.europeana.metis.schema.jibx.HasURL;
import eu.europeana.metis.schema.jibx.InScheme;
import eu.europeana.metis.schema.jibx.LiteralType;
import eu.europeana.metis.schema.jibx.Notation;
import eu.europeana.metis.schema.jibx.PersistentIdentifierType;
import eu.europeana.metis.schema.jibx.Pid;
import eu.europeana.metis.schema.jibx.ProxyType;
import eu.europeana.metis.schema.jibx.RDF;
import eu.europeana.metis.schema.jibx.ResourceOrLiteralType;
import eu.europeana.metis.schema.jibx.ResourceOrLiteralType.Resource;
import eu.europeana.metis.schema.jibx.ResourceType;
import eu.europeana.metis.schema.jibx.Value;
import eu.europeana.metis.schema.jibx.WebResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.stream.Streams;

/**
 * This class maintains a collection of normalized PIDs. It represents all persistent identifier
 * objects in a record. It can be added to and then written to a record. Note: PIDs that are not
 * referenced in the record through a <code>edm:pid</code> reference when creating this collection
 * will be ignored and not be written to a record (except if it is added as a normalized PID
 * later). This is checked when writing to a record.
 */
public class NormalizedPidsForRecord {

  // The PIDs known in the record, mapped by their about value.
  private final Map<String, PersistentIdentifierType> normalizedPids;

  /**
   * Constructor: initializes this class with all normalized PIDs in the record.
   *
   * @param edmRecord The record from which to obtain the normalized PIDs.
   */
  public NormalizedPidsForRecord(RDF edmRecord) {

    // Initialize the pre-existing PID map to contain all PIDs.
    this.normalizedPids = Streams.nonNull(edmRecord.getPersistentIdentifierList())
        .collect(Collectors.toMap(AboutType::getAbout, Function.identity()));
  }

  /**
   * Add all provided PIDs for a resource. Equivalence relationships will be established between all
   * present PID objects referenced from this resource. This method should only be called if there
   * are no known pid references, so that equivalency is established correctly.
   *
   * @param pidReferences  The pid references provided or otherwise known in this resource. Is not
   *                       <code>null</code>.
   * @param literalMatches The successful matches for <code>edm:pid</code> literals provided in the
   *                       resource. Is not <code>null</code>.
   * @return The set of PID object references that are to be set in the resource as
   * <code>edm:pid</code> references (overwriting the current list of such references and
   * literals). Is not <code>null</code>.
   */
  public Set<String> findOrAddAllProvidedPidsForResource(Set<String> pidReferences,
      List<PidSingleMatchResult> literalMatches) {
    return this.findOrAddAllPidsForResource(pidReferences,
        literalMatches.stream().map(List::of).map(PidMultipleMatchResult::forResults).toList());
  }

  /**
   * Add all discovered PIDs for a resource. Equivalence relationships will be established between
   * all present PID objects referenced from this resource. This method should be called with all
   * pid references so that equivalency is established correctly.
   *
   * @param discoveredMatches The successful matches for discovered PIDs provided in the resource.
   *                          Is not <code>null</code>.
   * @return The set of PID object references that are to be set in the resource as
   * <code>edm:pid</code> references (overwriting the current list of such references and
   * literals). Is not <code>null</code>.
   */
  public Set<String> findOrAddAllDiscoveredPidsForResource(
      Collection<PidMultipleMatchResult> discoveredMatches) {
    return this.findOrAddAllPidsForResource(Collections.emptySet(), discoveredMatches);
  }

  /**
   * Add all PIDs for a resource. Equivalence relationships will be established between all present
   * PID objects referenced from this resource. This method should only be called once for a
   * resource, so that all equivalences are computed correctly.
   *
   * @param existingReferences The pid references already known in this resource. Is not
   *                           <code>null</code>.
   * @param newMatches         The successful matches for PID literals encountered or discovered in
   *                           the resource. Is not <code>null</code>.
   * @return The set of PID object references that are to be set in the resource as
   * <code>edm:pid</code> references (overwriting the current list of such references and
   * literals). Is not <code>null</code>.
   */
  private Set<String> findOrAddAllPidsForResource(Set<String> existingReferences,
      Collection<PidMultipleMatchResult> newMatches) {

    // References go immediately to the result list (they are kept). Prepare the equivalency list.
    final Set<String> result = new HashSet<>(existingReferences);
    final Map<String, PersistentIdentifierType> equivalentPidObjects = new HashMap<>();

    // Find PID objects in the record that are referenced. They are considered for equivalency.
    existingReferences.stream().map(this.normalizedPids::get).filter(Objects::nonNull)
        .forEach(pid -> equivalentPidObjects.put(pid.getAbout(), pid));

    // Find or create PID objects for literal matches. Add them as reference and for equivalence.
    newMatches.stream().map(this::findOrAddNormalizedPid).forEach(pid -> {
      result.add(pid.getAbout());
      equivalentPidObjects.put(pid.getAbout(), pid);
    });

    // Add equivalence relations to all PID objects. Only reference known PIDs. Don't self-reference.
    if (equivalentPidObjects.size() > 1) {
      equivalentPidObjects.forEach((pidAbout, pid) -> {
        final Set<String> knownEquivalences = Streams.nonNull(pid.getEquivalentPIDList())
            .map(LiteralType::getString).filter(Objects::nonNull).collect(Collectors.toSet());
        knownEquivalences.addAll(equivalentPidObjects.keySet());
        knownEquivalences.remove(pidAbout);
        pid.setEquivalentPIDList(knownEquivalences.stream().map(equivalence -> {
          final EquivalentPID literal = new EquivalentPID();
          literal.setString(equivalence);
          return literal;
        }).toList());
      });
    }

    // Done. Return all references that are needed.
    return result;
  }

  /**
   * Attempts to find a known PID object by canonical PID value.
   *
   * @param canonicalPid The canonical PID to match. Is not <code>null</code>.
   * @return The PID object, or <code>null</code> if none is known.
   */
  private PersistentIdentifierType findByCanonicalPid(String canonicalPid) {
    for (PersistentIdentifierType candidate : this.normalizedPids.values()) {
      if (Optional.ofNullable(candidate.getValue()).map(LiteralType::getString)
          .filter(canonicalPid::equals).isPresent()) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Ensure that a PID normalization is part of this collection. It is added unless it already
   * exists (as determined by canonical PID value). The original PID value is added to the PID as a
   * notation (if it was not already there).
   *
   * @param normalization The PID normalization that should be part of this collection.
   * @return The normalized PID object within this collection. This value is not <code>null</code>.
   */
  private PersistentIdentifierType findOrAddNormalizedPid(PidMultipleMatchResult normalization) {

    // Try to find a matching PID object.
    final Optional<PersistentIdentifierType> existingPid = Optional
        .ofNullable(findByCanonicalPid(normalization.getCanonicalPid()));

    // If there is no matching PID object, create a new one and add it to the map.
    final PersistentIdentifierType pid = existingPid.orElseGet(() -> {
      final PersistentIdentifierType result = new PersistentIdentifierType();
      result.setAbout(computeNextPidAbout());
      result.setValue(new Value());
      result.getValue().setString(normalization.getCanonicalPid());
      if (StringUtils.isNotBlank(normalization.getSchemeId())) {
        result.setInScheme(new InScheme());
        result.getInScheme().setResource(normalization.getSchemeId());
      }
      normalizedPids.put(result.getAbout(), result);
      return result;
    });

    // Add each resolvable PID if it doesn't already exist.
    normalization.getResolvablePids().forEach(resolvablePid -> {
      final boolean addResolvablePidAsHasUrl = Streams.nonNull(pid.getHasURLList())
          .map(ResourceType::getResource).filter(Objects::nonNull)
          .noneMatch(resolvablePid::equals);
      if (addResolvablePidAsHasUrl) {
        if (pid.getHasURLList() == null) {
          pid.setHasURLList(new ArrayList<>());
        }
        final HasURL hasUrl = new HasURL();
        hasUrl.setResource(resolvablePid);
        pid.getHasURLList().add(hasUrl);
      }
    });

    // Add each original, unnormalized pid as a notation if different from the canonical or
    // a resolvable one (and if it doesn't already exist as a notation).
    normalization.getOriginalPids().forEach(originalPid -> {
      final boolean addOriginalPidAsNotation =
          !normalization.getCanonicalPid().equals(originalPid) &&
              !normalization.getResolvablePids().contains(originalPid) &&
              Streams.nonNull(pid.getNotationList()).map(LiteralType::getString)
                  .filter(Objects::nonNull).noneMatch(originalPid::equals);
      if (addOriginalPidAsNotation) {
        if (pid.getNotationList() == null) {
          pid.setNotationList(new ArrayList<>());
        }
        final Notation notation = new Notation();
        notation.setString(originalPid);
        pid.getNotationList().add(notation);
      }
    });

    // Done.
    return pid;
  }

  private String computeNextPidAbout() {
    for (int i = 0; ; i++) {
      final String proposedId = "#pid_" + i;
      if (!normalizedPids.containsKey(proposedId)) {
        return proposedId;
      }
    }
  }

  /**
   * Write this collection to the record. All existing PID objects are removed/overwritten.
   *
   * @param edmRecord The record to which to write the collection.
   */
  public void writeToRecord(RDF edmRecord) {

    // Get the list of PID objects referenced from the record.
    final Stream<List<Pid>> pidReferencesInProxy = Streams.nonNull(edmRecord.getProxyList())
        .map(ProxyType::getPidList);
    final Stream<List<Pid>> pidReferencesInWebResource = Streams
        .nonNull(edmRecord.getWebResourceList()).map(WebResourceType::getPidList);
    final Set<String> pidReferences =
        Stream.concat(pidReferencesInProxy, pidReferencesInWebResource).filter(Objects::nonNull)
        .flatMap(Collection::stream).filter(Objects::nonNull)
        .map(ResourceOrLiteralType::getResource).filter(Objects::nonNull)
        .map(Resource::getResource).filter(Objects::nonNull)
        .collect(Collectors.toSet());

    // Write the referenced PID objects to the record.
    edmRecord.setPersistentIdentifierList(
        pidReferences.stream().map(this.normalizedPids::get).filter(Objects::nonNull).toList());
  }
}
