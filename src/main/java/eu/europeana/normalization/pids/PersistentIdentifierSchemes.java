package eu.europeana.normalization.pids;

import com.fasterxml.jackson.annotation.JsonRootName;
import java.util.Collection;
import java.util.List;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * This represents a list of persistent identifier schemes as it is modeled in the vocabulary file.
 */
@JsonRootName(namespace = PersistentIdentifierSchemes.RDF_NAMESPACE, value = "RDF")
public class PersistentIdentifierSchemes {

  public static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static final String EDM_NAMESPACE = "http://www.europeana.eu/schemas/edm/";

  @JacksonXmlProperty(namespace = EDM_NAMESPACE, localName = "PersistentIdentifierScheme")
  @JacksonXmlElementWrapper(useWrapping = false)
  private List<PersistentIdentifierScheme> schemes;

  public Collection<PersistentIdentifierScheme> getSchemes() {
    return schemes;
  }
}
