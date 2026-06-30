package eu.europeana.normalization.pids;

import eu.europeana.metis.schema.jibx.AboutType;
import eu.europeana.metis.schema.jibx.HasURL;
import eu.europeana.metis.schema.jibx.InScheme;
import eu.europeana.metis.schema.jibx.LiteralType;
import eu.europeana.metis.schema.jibx.Notation;
import eu.europeana.metis.schema.jibx.PersistentIdentifierType;
import eu.europeana.metis.schema.jibx.RDF;
import eu.europeana.metis.schema.jibx.ResourceType;
import eu.europeana.metis.schema.jibx.Value;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class maintains a collection of normalized PIDs. It represents all persistent identifier
 * objects in a record. It can be added to and then written to a record.
 */
public class NormalizedPids {

  private final Map<String, PersistentIdentifierType> normalizedPidsById;

  /**
   * Constructor: initializes this class with all normalized PIDs in the record.
   *
   * @param edmRecord The record from which to obtain the normalized PIDs.
   */
  public NormalizedPids(RDF edmRecord) {
    this.normalizedPidsById = Optional.ofNullable(edmRecord.getPersistentIdentifierList())
        .stream().flatMap(Collection::stream)
        .collect(Collectors.toMap(AboutType::getAbout, Function.identity()));
  }

  /**
   * Write this collection to the record. All existing PID objects are removed/overwritten.
   *
   * @param edmRecord The record to which to write the collection.
   */
  public void writeToRecord(RDF edmRecord) {
    edmRecord.setPersistentIdentifierList(new ArrayList<>(normalizedPidsById.values()));
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
    final Optional<PersistentIdentifierType> existingPid = normalizedPidsById.values().stream()
        .filter(candidate -> Optional.ofNullable(candidate.getValue()).map(LiteralType::getString)
            .filter(normalization.getCanonicalPid()::equals).isPresent())
        .findAny();

    // If there is no matching PID object, create a new one and add it to the map.
    final PersistentIdentifierType pid = existingPid.orElseGet(() -> {
      final PersistentIdentifierType result = new PersistentIdentifierType();
      result.setAbout(computeNextPidAbout());
      result.setValue(new Value());
      result.getValue().setString(normalization.getCanonicalPid());
      result.setInScheme(new InScheme());
      result.getInScheme().setResource(normalization.getScheme().getSchemeId());
      normalizedPidsById.put(result.getAbout(), result);
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

    // Done.
    return pid.getAbout();
  }

  private String computeNextPidAbout() {
    for (int i = 0; ; i++) {
      final String proposedId = "#pid_" + i;
      if (!normalizedPidsById.containsKey(proposedId)) {
        return proposedId;
      }
    }
  }
}
