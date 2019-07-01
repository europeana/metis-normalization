package eu.europeana.normalization.util;

import eu.europeana.normalization.util.Namespace.Element;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * This object represents an XPath query, containing an expression and the namespaces that occur in
 * it. This query can be executed on a DOM tree. Internally it uses the
 * {@link javax.xml.xpath.XPath} API.
 *
 * @author jochen
 *
 */
public final class XpathQuery {

  /** The element rdf:RDF that may be used to create queries. **/
  public static final Element RDF_TAG = Namespace.RDF.getElement("RDF");

  private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

  private final Map<String, String> namespaceMap = new HashMap<>();
  private final String expressionFormat;
  private final String expression;
  private final List<Element> elements;

  /**
   * Constructor. It uses {@link String#format(String, Object...)} to format the given input format
   * to put in the elements.
   *
   * @param expressionFormat A format string with '%s' placeholders (see {@link
   * java.util.Formatter}).
   * @param elements The elements corresponding to the placeholders in the expression format.
   */
  public XpathQuery(String expressionFormat, Element... elements) {

    // Save the properties
    this.expressionFormat = expressionFormat;
    this.elements = Arrays.asList(elements);

    // Compute the namespace map
    final Set<String> namespaces = Arrays.stream(elements).map(Element::getNamespace)
        .map(Namespace::getUri).collect(Collectors.toSet());
    final Map<String, String> reverseNamespaceMap = new HashMap<>(namespaces.size());
    int counter = 0;
    for (String namespace : namespaces) {
      counter++;
      final String prefix = "ns" + counter;
      this.namespaceMap.put(prefix, namespace);
      reverseNamespaceMap.put(namespace, prefix);
    }

    // Compute the expression
    final Object[] parameters = this.elements.stream().map(element -> elementToString(element,
        reverseNamespaceMap.get(element.getNamespace().getUri()))).toArray(Object[]::new);
    this.expression = String.format(expressionFormat, parameters).trim();
  }

  private static String elementToString(Element element, String prefix) {
    return XmlUtil.addPrefixToNodeName(element.getElementName(), prefix);
  }

  /**
   * This is a convenience method for combining XPath queries into one. That means it will match on
   * the union of the two previous queries. It does so by concatenating the expressions using the
   * '|' deliminators and merging the namespace maps.
   *
   * @param queries The queries to combine.
   * @return A query representing the combination of the input queries.
   * @throws IllegalArgumentException In case the queries could not be combined because the
   *         namespaces conflict (i.e. there are two namespaces with the same prefix but a different
   *         URI).
   */
  public static XpathQuery combine(XpathQuery... queries) {
    final String expressionFormat = Arrays.stream(queries).map(query -> query.expressionFormat)
        .collect(Collectors.joining(" | "));
    final Element[] elements = Arrays.stream(queries).map(query -> query.elements)
        .flatMap(List::stream).toArray(Element[]::new);
    return new XpathQuery(expressionFormat, elements);
  }

  private XPathExpression toXPath() throws XPathExpressionException {
    XPath xpath = XPATH_FACTORY.newXPath();
    xpath.setNamespaceContext(new SimpleNamespaceContext());
    return xpath.compile(this.expression);
  }

  /**
   * This method executes the query on a DOM tree.
   *
   * @param dom The DOM tree on which to execute the query.
   * @return The list of nodes that satisfy the query. Is not null, but could of course be empty.
   * @throws XPathExpressionException In case the expression couldn't be evaluated.
   */
  public NodeList execute(Document dom) throws XPathExpressionException {
    return (NodeList) toXPath().evaluate(dom, XPathConstants.NODESET);
  }

  private class SimpleNamespaceContext implements NamespaceContext {

    @Override
    public String getNamespaceURI(String prefix) {

      // In case prefix is null.
      if (prefix == null) {
        throw new IllegalArgumentException();
      }

      // In case prefix is in map.
      final String resultFromMap = namespaceMap.get(prefix);
      if (resultFromMap != null) {
        return resultFromMap;
      }

      // Other default options.
      final String result;
      switch (prefix) {
        case XMLConstants.XML_NS_PREFIX:
          result = XMLConstants.XML_NS_URI;
          break;
        case XMLConstants.XMLNS_ATTRIBUTE:
          result = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
          break;
        default:
          result = XMLConstants.NULL_NS_URI;
          break;
      }

      // Done.
      return result;
    }

    @Override
    public String getPrefix(String uri) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<String> getPrefixes(String uri) {
      throw new UnsupportedOperationException();
    }
  }
}
