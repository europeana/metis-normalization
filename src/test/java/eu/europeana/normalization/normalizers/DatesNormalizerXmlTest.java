package eu.europeana.normalization.normalizers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.params.provider.Arguments.of;

import eu.europeana.normalization.model.RecordWrapper;
import eu.europeana.normalization.util.Namespace;
import eu.europeana.normalization.util.NormalizationException;
import eu.europeana.normalization.util.XmlUtil;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class DatesNormalizerXmlTest {

  private final DatesNormalizer normalizer = new DatesNormalizer();

  @Test
  void collectsOriginalValuesFromDifferentProperties() throws NormalizationException {
    final Document document = normalize("""
        <dc:date>190X]</dc:date>
        <dcterms:issued xml:lang="en">190X</dcterms:issued>
        <dcterms:created>190X</dcterms:created>
        """);
    final Element timeSpan = getTimeSpan(document);

    assertEquals("#190X", timeSpan.getAttributeNS(Namespace.RDF.getUri(), "about"));
    assertLabels(timeSpan, "prefLabel", new Label("190X", "zxx"));
    assertLabels(timeSpan, "hiddenLabel", new Label("190X", "en"), new Label("190X]", ""));
    assertDateValues(timeSpan, "1900-01-01", "1909-12-31", "190X");
    assertEquals(List.of("http://data.europeana.eu/timespan/19", "http://data.europeana.eu/timespan/20"),
        children(timeSpan, Namespace.DCTERMS, "isPartOf").stream()
            .map(element -> element.getAttributeNS(Namespace.RDF.getUri(), "resource")).toList());
    final Element europeanaProxy = XmlUtil.getAsElementList(document.getElementsByTagNameNS(
        Namespace.ORE.getUri(), "Proxy")).get(1);
    assertEquals(List.of("#190X", "#190X", "#190X"), XmlUtil.elements(europeanaProxy).stream()
        .filter(element -> element.hasAttributeNS(Namespace.RDF.getUri(), "resource"))
        .map(element -> element.getAttributeNS(Namespace.RDF.getUri(), "resource")).toList());
  }

  @ParameterizedTest
  @MethodSource("annotatedDates")
  void preservesAnnotationsInLabelsAndReferences(String original, String language, String uri,
      String begin, String end, String notation) throws NormalizationException {
    final String field = dateField(original, language);
    final Document document = normalize(field + field);
    final Element timeSpan = getTimeSpan(document);

    assertEquals(uri, timeSpan.getAttributeNS(Namespace.RDF.getUri(), "about"));
    assertLabels(timeSpan, "prefLabel", new Label(original, language));
    assertLabels(timeSpan, "hiddenLabel");
    assertDateValues(timeSpan, begin, end, notation);
    final Element europeanaProxy = XmlUtil.getAsElementList(document.getElementsByTagNameNS(
        Namespace.ORE.getUri(), "Proxy")).get(1);
    assertEquals(List.of(uri, uri), children(europeanaProxy, Namespace.DC, "date").stream()
        .map(element -> element.getAttributeNS(Namespace.RDF.getUri(), "resource")).toList());
    if (language.isEmpty()) {
      assertFalse(children(timeSpan, Namespace.SKOS, "prefLabel").getFirst()
          .hasAttributeNS(Namespace.XML.getUri(), "lang"));
    }
  }

  private static Stream<Arguments> annotatedDates() {
    return Stream.of(
        of("2012-11-XX (recording)", "en", "#2012-11-XX%20(recording)", "2012-11-01", "2012-11-30", "2012-11"),
        of("189u [questionable]", "en", "#189u%20%5Bquestionable%5D", "1890-01-01", "1899-12-31", "189X"),
        of("2012-11-XX (recording)", "", "#2012-11-XX%20(recording)", "2012-11-01", "2012-11-30", "2012-11"),
        of("189u [questionable]", "", "#189u%20%5Bquestionable%5D", "1890-01-01", "1899-12-31", "189X"),
        of("(recording) 2012-11-XX", "en", "#(recording)%202012-11-XX", "2012-11-01", "2012-11-30", "2012-11"),
        of(" 189u [questionable] ", "nl", "#%20189u%20%5Bquestionable%5D%20", "1890-01-01", "1899-12-31", "189X"),
        of("189u [a|b]", "en", "#189u%20%5Ba%7Cb%5D", "1890-01-01", "1899-12-31", "189X")
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "en", "EN-gb"})
  void omitsDuplicateHiddenLabels(String language) throws NormalizationException {
    final String field = dateField("190X]", language);
    final Element timeSpan = getTimeSpan(normalize(field + field + field));

    assertLabels(timeSpan, "hiddenLabel", new Label("190X]", language));
    if (language.isEmpty()) {
      assertFalse(children(timeSpan, Namespace.SKOS, "hiddenLabel").getFirst()
          .hasAttributeNS(Namespace.XML.getUri(), "lang"));
    }
  }

  @Test
  void retainsHiddenLabelsForDifferentLanguages() throws NormalizationException {
    final Element timeSpan = getTimeSpan(normalize(
        dateField("190X]", "en") + dateField("190X]", "nl") + dateField("190X]", "")));

    assertLabels(timeSpan, "hiddenLabel", new Label("190X]", "en"), new Label("190X]", "nl"), new Label("190X]", ""));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "en", "EN-gb"})
  void keepsLanguageOnDcmiName(String language) throws NormalizationException {
    final String original = "name=Modern era; start=1975; end=1980;";
    final String field = dateField(original, language);
    final Element timeSpan = getTimeSpan(normalize(field + field));

    assertLabels(timeSpan, "prefLabel", new Label("Modern era", language));
    assertLabels(timeSpan, "hiddenLabel", new Label(original, language));
    assertDateValues(timeSpan, "1975-01-01", "1980-12-31", "1975/1980");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "en"})
  void usesEdtfWhenDcmiNameIsAbsent(String language) throws NormalizationException {
    final String original = "start=1975; end=1980;";
    final Element timeSpan = getTimeSpan(normalize(dateField(original, language)));

    assertLabels(timeSpan, "prefLabel", new Label("1975/1980", "zxx"));
    assertLabels(timeSpan, "hiddenLabel", new Label(original, language));
    assertDateValues(timeSpan, "1975-01-01", "1980-12-31", "1975/1980");
  }

  @ParameterizedTest
  @MethodSource("matchingLabels")
  void checksEveryLabelBeforeAddingOriginalLiteral(String labelType, String labelLanguage,
      String originalLanguage, int expectedHiddenLabels) throws NormalizationException {
    final Element timeSpan = getTimeSpan(normalize(dateField("190X", "")));
    final Element label = XmlUtil.createElement(Namespace.SKOS.getElement(labelType), timeSpan, null);
    label.setTextContent("190X]");
    if (!labelLanguage.isEmpty()) {
      label.setAttributeNS(Namespace.XML.getUri(), "xml:lang", labelLanguage);
    }
    normalizer.includeInTimeSpanEntity(timeSpan, "190X]", originalLanguage);

    assertEquals(expectedHiddenLabels, children(timeSpan, Namespace.SKOS, "hiddenLabel").size());
  }

  private static Stream<Arguments> matchingLabels() {
    return Stream.of(
        of("prefLabel", "en", "en", 0),
        of("prefLabel", "", "", 0),
        of("prefLabel", "zxx", "", 0),
        of("altLabel", "en", "en", 0),
        of("altLabel", "", "", 0),
        of("hiddenLabel", "en", "en", 1),
        of("hiddenLabel", "", "", 1),
        of("altLabel", "en", "nl", 1),
        of("altLabel", "en", "EN", 1)
    );
  }

  private Document normalize(String fields) throws NormalizationException {
    return normalize(fields, normalizer);
  }

  private Document normalize(String fields, DatesNormalizer datesNormalizer) throws NormalizationException {
    final String xmlRecord = """
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
            xmlns:edm="http://www.europeana.eu/schemas/edm/"
            xmlns:skos="http://www.w3.org/2004/02/skos/core#"
            xmlns:dc="http://purl.org/dc/elements/1.1/"
            xmlns:dcterms="http://purl.org/dc/terms/"
            xmlns:ore="http://www.openarchives.org/ore/terms/">
          <ore:Proxy rdf:about="provider">%s</ore:Proxy>
          <ore:Proxy rdf:about="europeana"><edm:europeanaProxy>true</edm:europeanaProxy></ore:Proxy>
        </rdf:RDF>
        """.formatted(fields);
    return datesNormalizer.normalize(RecordWrapper.create(xmlRecord)).edmRecord().getAsDocument();
  }

  private static String dateField(String value, String language) {
    final String languageAttribute = language.isEmpty() ? "" : " xml:lang=\"" + language + "\"";
    return "<dc:date" + languageAttribute + ">" + value + "</dc:date>";
  }

  private static Element getTimeSpan(Document document) {
    final List<Element> timeSpans = XmlUtil.getAsElementList(
        document.getElementsByTagNameNS(Namespace.EDM.getUri(), "TimeSpan"));
    assertEquals(1, timeSpans.size());
    return timeSpans.getFirst();
  }

  private static List<Element> children(Element parent, Namespace namespace, String name) {
    return XmlUtil.getAsElementList(parent.getElementsByTagNameNS(namespace.getUri(), name));
  }

  private static void assertLabels(Element timeSpan, String labelType, Label... expected) {
    final Comparator<Label> order = Comparator.comparing(Label::text).thenComparing(Label::languageTag);
    final List<Label> actual = children(timeSpan, Namespace.SKOS, labelType).stream()
        .map(element -> new Label(element.getTextContent(), element.getAttributeNS(Namespace.XML.getUri(), "lang")))
        .sorted(order).toList();
    assertEquals(Stream.of(expected).sorted(order).toList(), actual);
  }

  private static void assertDateValues(Element timeSpan, String begin, String end, String notation) {
    assertEquals(begin, children(timeSpan, Namespace.EDM, "begin").getFirst().getTextContent());
    assertEquals(end, children(timeSpan, Namespace.EDM, "end").getFirst().getTextContent());
    final Element notationElement = children(timeSpan, Namespace.SKOS, "notation").getFirst();
    assertEquals(notation, notationElement.getTextContent());
    assertEquals("http://id.loc.gov/datatypes/edtf/EDTF-level1",
        notationElement.getAttributeNS(Namespace.RDF.getUri(), "datatype"));
  }

  private record Label(String text, String languageTag) {
  }
}
