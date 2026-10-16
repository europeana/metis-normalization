package eu.europeana.normalization.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Instances of this class represent an XML namespace (with tag prefix and URI).
 */
@Getter
@AllArgsConstructor
public enum Namespace {

  XML("http://www.w3.org/XML/1998/namespace", "xml"),
  RDF("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf"),
  EDM("http://www.europeana.eu/schemas/edm/", "edm"),
  ORE("http://www.openarchives.org/ore/terms/", "ore"),
  SKOS("http://www.w3.org/2004/02/skos/core#", "skos"),
  DC("http://purl.org/dc/elements/1.1/", "dc"),
  DCTERMS("http://purl.org/dc/terms/", "dcterms");

  private final String uri;
  private final String suggestedPrefix;

  /**
   * This method creates an instance of {@link Element} for this namespace and the given element name.
   *
   * @param elementName The element name.
   * @return The element.
   */
  public Element getElement(String elementName) {
    return new Element(elementName, this);
  }

  /**
   * This class represents an XML element.
   */
  public record Element(String elementName, Namespace namespace) {

  }
}
