package eu.europeana.normalization.pids;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.Set;

public class PersistentIdentifierScheme {

  private static final String DCTERMS_NAMESPACE = "http://purl.org/dc/terms/";
  private static final String EDM_NAMESPACE = "http://www.europeana.eu/schemas/edm/";
  private static final String DOAP_NAMESPACE = "http://usefulinc.com/ns/doap#";
  private static final String RDFS_NAMESPACE = "https://www.w3.org/TR/rdf-schema/#";
  public static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

  @JacksonXmlProperty(namespace = RDF_NAMESPACE, localName = "about", isAttribute = true)
  private String about;

  @JacksonXmlProperty(namespace = DCTERMS_NAMESPACE, localName = "title")
  private String title;

  @JacksonXmlProperty(namespace = RDFS_NAMESPACE, localName = "seeAlso")
  private String seeAlso;

  @JacksonXmlProperty(namespace = DOAP_NAMESPACE, localName = "maintainer")
  private String maintainer;

  @JacksonXmlProperty(namespace = EDM_NAMESPACE, localName = "canonicalPattern")
  private String canonicalPattern;

  @JacksonXmlProperty(namespace = EDM_NAMESPACE, localName = "resolvablePattern")
  private String resolvablePattern;

  @JacksonXmlProperty(namespace = EDM_NAMESPACE, localName = "matchingPattern")
  @JacksonXmlElementWrapper(useWrapping = false)
  private Set<String> matchingPatterns;

  public String getAbout() {
    return about;
  }

  public String getTitle() {
    return title;
  }

  public String getSeeAlso() {
    return seeAlso;
  }

  public String getMaintainer() {
    return maintainer;
  }

  public String getCanonicalPattern() {
    return canonicalPattern;
  }

  public String getResolvablePattern() {
    return resolvablePattern;
  }

  public Set<String> getMatchingPatterns() {
    return matchingPatterns;
  }
}
