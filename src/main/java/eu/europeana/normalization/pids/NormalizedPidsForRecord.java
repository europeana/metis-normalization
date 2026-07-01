package eu.europeana.normalization.pids;

import eu.europeana.metis.schema.jibx.AboutType;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class maintains a collection of normalized PIDs. It represents all persistent identifier
 * objects in a record. It can be added to and then written to a record.
 */
public class NormalizedPidsForRecord {

  // Keeps track of the PIDs encountered in the record that are not (yet) referenced.
  private final Map<String, PersistentIdentifierType> unreferencedPids;

  // Keeps track of the PIDs that are to be added/retained in the record (i.e., are referenced).
  private final Map<String, PersistentIdentifierType> referencedPids = new HashMap<>();

  /**
   * Constructor: initializes this class with all normalized PIDs in the record.
   *
   * @param edmRecord The record from which to obtain the normalized PIDs.
   */
  public NormalizedPidsForRecord(RDF edmRecord) {

    // Initialize the pre-existing PID map to contain all PIDs.
    this.unreferencedPids = Optional.ofNullable(edmRecord.getPersistentIdentifierList())
        .stream().flatMap(Collection::stream)
        .collect(Collectors.toMap(AboutType::getAbout, Function.identity()));

    // Record all referenced PID objects.
    final Stream<List<Pid>> pidReferencesInProxy = Optional.ofNullable(edmRecord.getProxyList())
        .stream().flatMap(Collection::stream).filter(Objects::nonNull)
        .map(ProxyType::getPidList);
    pidReferencesInProxy.filter(Objects::nonNull)
        .flatMap(Collection::stream).filter(Objects::nonNull)
        .map(ResourceOrLiteralType::getResource).filter(Objects::nonNull)
        .map(Resource::getResource).filter(Objects::nonNull)
        .forEach(this::recordReference);
  }

  /**
   * Called in case we find a reference to a PID. This moves the PID object from the unreferenced
   * to the referenced PID lists if needed.
   *
   * @param pidReference The references.
   */
  private void recordReference(String pidReference) {
    Optional.ofNullable(unreferencedPids.remove(pidReference))
        .ifPresent(pid -> referencedPids.put(pidReference, pid));
  }

  /**
   * Write this collection to the record. All existing PID objects are removed/overwritten.
   *
   * @param edmRecord The record to which to write the collection.
   */
  public void writeToRecord(RDF edmRecord) {
    edmRecord.setPersistentIdentifierList(new ArrayList<>(referencedPids.values()));
  }

  /**
   * Attempts to find a known PID object by canonical PID value. If we find a pre-existing one, we
   * notify that a reference has been found.
   *
   * @param canonicalPid The canonical PID to match. Is not <code>null</code>.
   * @return The PID object, or <code>null</code> if none is known.
   */
  private PersistentIdentifierType findByCanonicalPid(String canonicalPid) {

    // First check in the already referenced PIDs.
    for (PersistentIdentifierType candidate : this.referencedPids.values()) {
      if (Optional.ofNullable(candidate.getValue()).map(LiteralType::getString)
          .filter(canonicalPid::equals).isPresent()) {
        return candidate;
      }
    }

    // Then check in the non-referenced PIDs.
    for (PersistentIdentifierType candidate : this.unreferencedPids.values()) {
      if (Optional.ofNullable(candidate.getValue()).map(LiteralType::getString)
          .filter(canonicalPid::equals).isPresent()) {
        return candidate;
      }
    }

    // We didn't find the canonical PID.
    return null;
  }

  /**
   * Ensure that a PID normalization is part of this collection. It is added unless it already
   * exists (as determined by canonical PID value). The original PID value is added to the PID as a
   * notation (if it was not already there).
   *
   * @param normalization The PID normalization that should be part of this collection.
   * @return The <code>rdf:about</code> value assigned to this PID normalization within this
   * collection. This value is not null or empty.
   */
  public String findOrAddNormalizedPid(PidMultipleMatchResult normalization) {

    // Try to find a matching PID object.
    final Optional<PersistentIdentifierType> existingPid = Optional
        .ofNullable(findByCanonicalPid(normalization.getCanonicalPid()));

    // If there is no matching PID object, create a new one and add it to the map.
    final PersistentIdentifierType pid = existingPid.orElseGet(() -> {
      final PersistentIdentifierType result = new PersistentIdentifierType();
      result.setAbout(computeNextPidAbout());
      result.setValue(new Value());
      result.getValue().setString(normalization.getCanonicalPid());
      result.setInScheme(new InScheme());
      result.getInScheme().setResource(normalization.getScheme().getSchemeId());
      referencedPids.put(result.getAbout(), result);
      return result;
    });

    // Add each resolvable PID if it doesn't already exist.
    normalization.getResolvablePids().forEach(resolvablePid -> {
      final boolean addResolvablePidAsHasUrl = Optional.ofNullable(pid.getHasURLList()).stream()
          .flatMap(Collection::stream).filter(Objects::nonNull)
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
              Optional.ofNullable(pid.getNotationList()).stream()
                  .flatMap(Collection::stream).filter(Objects::nonNull)
                  .map(LiteralType::getString).filter(Objects::nonNull)
                  .noneMatch(originalPid::equals);
      if (addOriginalPidAsNotation) {
        if (pid.getNotationList() == null) {
          pid.setNotationList(new ArrayList<>());
        }
        final Notation notation = new Notation();
        notation.setString(originalPid);
        pid.getNotationList().add(notation);
      }
    });

    // Done. Record the new reference.
    recordReference(pid.getAbout());
    return pid.getAbout();
  }

  private String computeNextPidAbout() {
    for (int i = 0; ; i++) {
      final String proposedId = "#pid_" + i;
      if (!referencedPids.containsKey(proposedId)) {
        return proposedId;
      }
    }
  }
}
