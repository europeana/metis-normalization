package eu.europeana.normalization.normalizers;

import static eu.europeana.normalization.dates.DateNormalizationExtractorMatchId.DCMI_PERIOD;
import static eu.europeana.normalization.dates.DateNormalizationResultStatus.MATCHED;
import static eu.europeana.normalization.dates.DateNormalizationResultStatus.NO_MATCH;
import static java.util.function.Predicate.not;

import eu.europeana.normalization.dates.DateNormalizationResult;
import eu.europeana.normalization.dates.edtf.AbstractEdtfDate;
import eu.europeana.normalization.dates.edtf.DateQualification;
import eu.europeana.normalization.dates.edtf.InstantEdtfDate;
import eu.europeana.normalization.dates.extraction.extractors.BcAdDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.BcAdRangeDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.BriefRangeDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.CenturyNumericDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.CenturyRomanDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.CenturyRomanRangeDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.DateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.DcmiPeriodDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.DecadeDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.EdtfDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.EdtfRangeDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.FullDateDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.LongNegativeYearDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.LongNegativeYearRangeDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.MonthNameDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.NumericPartsDateExtractor;
import eu.europeana.normalization.dates.extraction.extractors.NumericPartsRangeDateExtractor;
import eu.europeana.normalization.dates.sanitize.DateFieldSanitizer;
import eu.europeana.normalization.dates.sanitize.SanitizeOperation;
import eu.europeana.normalization.dates.sanitize.SanitizedDate;
import eu.europeana.normalization.model.ConfidenceLevel;
import eu.europeana.normalization.model.NormalizeActionResult;
import eu.europeana.normalization.model.RecordWrapper;
import eu.europeana.normalization.util.Namespace;
import eu.europeana.normalization.util.NormalizationException;
import eu.europeana.normalization.util.XmlUtil;
import eu.europeana.normalization.util.XpathQuery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.xml.xpath.XPathExpressionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The main class that implements the normalisation procedure.
 * <p>
 * It provides procedures for normalising values of properties that:
 *   <ul>
 *     <li>should contain date values.</li>
 *     <li>may contain dates as well as other kinds of entities (i.e., dc:subject and dc:coverage).</li>
 *   </ul>
 * </p>
 */
@Slf4j
public class DatesNormalizer implements RecordNormalizeAction {

  private static final int MAX_VOCABULARY_CENTURY = 21;

  private static final Namespace.Element EDM_PROVIDED_CHO = Namespace.EDM.getElement("ProvidedCHO");
  private static final Namespace.Element EDM_WEB_RESOURCE = Namespace.EDM.getElement("WebResource");
  private static final Namespace.Element EDM_AGENT = Namespace.EDM.getElement("Agent");
  private static final Namespace.Element EDM_PLACE = Namespace.EDM.getElement("Place");
  private static final Namespace.Element EDM_TIMESPAN = Namespace.EDM.getElement("TimeSpan");
  private static final Namespace.Element RDF_ABOUT = Namespace.RDF.getElement("about");
  private static final Namespace.Element SKOS_PREF_LABEL = Namespace.SKOS.getElement("prefLabel");
  private static final Namespace.Element SKOS_ALT_LABEL = Namespace.SKOS.getElement("altLabel");
  private static final Namespace.Element SKOS_HIDDEN_LABEL = Namespace.SKOS.getElement("hiddenLabel");
  private static final List<Namespace.Element> SKOS_LABELS = List.of(SKOS_PREF_LABEL, SKOS_ALT_LABEL, SKOS_HIDDEN_LABEL);
  private static final Namespace.Element XML_LANG = Namespace.XML.getElement("lang");
  private static final Namespace.Element SKOS_NOTATION = Namespace.SKOS.getElement("notation");
  private static final Namespace.Element SKOS_NOTE = Namespace.SKOS.getElement("note");
  private static final Namespace.Element RDF_DATATYPE = Namespace.RDF.getElement("datatype");
  private static final Namespace.Element RDF_RESOURCE = Namespace.RDF.getElement("resource");
  private static final Namespace.Element EDM_BEGIN = Namespace.EDM.getElement("begin");
  private static final Namespace.Element EDM_END = Namespace.EDM.getElement("end");
  private static final Namespace.Element DC_TERMS_IS_PART_OF = Namespace.DCTERMS.getElement("isPartOf");
  private static final Namespace.Element ORE_PROXY = Namespace.ORE.getElement("Proxy");
  private static final Namespace.Element EDM_EUROPEANA_PROXY = Namespace.EDM.getElement("europeanaProxy");

  private static final Pair<Namespace.Element, XpathQuery> PROXY_QUERY_CREATED = getProxySubtagQuery(
      Namespace.DCTERMS.getElement("created"));

  private static final Pair<Namespace.Element, XpathQuery> PROXY_QUERY_ISSUED = getProxySubtagQuery(
      Namespace.DCTERMS.getElement("issued"));

  private static final Pair<Namespace.Element, XpathQuery> PROXY_QUERY_TEMPORAL = getProxySubtagQuery(
      Namespace.DCTERMS.getElement("temporal"));

  private static final Pair<Namespace.Element, XpathQuery> PROXY_QUERY_DATE = getProxySubtagQuery(
      Namespace.DC.getElement("date"));

  private static final Pair<Namespace.Element, XpathQuery> PROXY_QUERY_COVERAGE = getProxySubtagQuery(
      Namespace.DC.getElement("coverage"));

  private static final Pair<Namespace.Element, XpathQuery> PROXY_QUERY_SUBJECT = getProxySubtagQuery(
      Namespace.DC.getElement("subject"));

  private static final List<Pair<Namespace.Element, XpathQuery>> DATE_PROPERTY_FIELDS = List.of(
      PROXY_QUERY_CREATED, PROXY_QUERY_ISSUED, PROXY_QUERY_TEMPORAL, PROXY_QUERY_DATE, PROXY_QUERY_COVERAGE);
  private static final List<Pair<Namespace.Element, XpathQuery>> GENERIC_PROPERTY_FIELDS = List.of(PROXY_QUERY_SUBJECT);

  private static final XpathQuery EUROPEANA_PROXY = new XpathQuery("/%s/%s[%s='true']",
      XpathQuery.RDF_TAG, ORE_PROXY, EDM_EUROPEANA_PROXY);

  private final DateFieldSanitizer dateFieldSanitizer = new DateFieldSanitizer();

  private final List<DateExtractor> extractorsInOrderForDateProperties;
  private final List<DateExtractor> extractorsInOrderForGenericProperties;
  private final List<Function<String, DateNormalizationResult>> normalizationOperationsInOrderDateProperty;
  private final List<Function<String, DateNormalizationResult>> normalizationOperationsInOrderGenericProperty;

  /**
   * Default constructor.
   * <p>Initializes all the internal required properties</p>
   */
  public DatesNormalizer() {
    // The pattern PatternBriefDateRangeDateExtractor needs to be executed before the EDTF pattern.
    // Most values that match this pattern also match the EDTF pattern, but would result in an invalid date.
    // This pattern only matches values that would not be valid EDTF dates.
    extractorsInOrderForDateProperties = List.of(
        new BriefRangeDateExtractor(),
        new EdtfDateExtractor(),
        new EdtfRangeDateExtractor(),
        new CenturyNumericDateExtractor(),
        new CenturyRomanDateExtractor(),
        new CenturyRomanRangeDateExtractor(),
        new DecadeDateExtractor(),
        new NumericPartsRangeDateExtractor(),
        new NumericPartsDateExtractor(),
        new DcmiPeriodDateExtractor(),
        new MonthNameDateExtractor(),
        new FullDateDateExtractor(),
        new BcAdDateExtractor(),
        new BcAdRangeDateExtractor(),
        new LongNegativeYearDateExtractor(),
        new LongNegativeYearRangeDateExtractor());

    extractorsInOrderForGenericProperties =
        extractorsInOrderForDateProperties.stream()
                                          .filter(not(BriefRangeDateExtractor.class::isInstance)).toList();

    normalizationOperationsInOrderDateProperty = List.of(
        input -> normalizeInput(extractorsInOrderForDateProperties, input),
        input -> normalizeInputSanitized(extractorsInOrderForDateProperties, input,
            dateFieldSanitizer::sanitize1stTimeDateProperty,
            SanitizeOperation::isApproximateSanitizeOperationForDateProperty,
            (dateExtractors, sanitizedDate) -> normalizeInput(dateExtractors, sanitizedDate.sanitizedDateString())),
        input -> normalizeInputSanitized(extractorsInOrderForDateProperties, input,
            dateFieldSanitizer::sanitize2ndTimeDateProperty,
            SanitizeOperation::isApproximateSanitizeOperationForDateProperty,
            (dateExtractors, sanitizedDate) -> normalizeInput(dateExtractors, sanitizedDate.sanitizedDateString())));

    normalizationOperationsInOrderGenericProperty = List.of(
        input -> normalizeInputGeneric(extractorsInOrderForGenericProperties, input),
        input -> normalizeInputSanitized(extractorsInOrderForGenericProperties, input,
            dateFieldSanitizer::sanitizeGenericProperty,
            SanitizeOperation::isApproximateSanitizeOperationForGenericProperty,
            (dateExtractors, sanitizedDate) -> normalizeInputGeneric(dateExtractors, sanitizedDate.sanitizedDateString())));
  }

  private static Pair<Namespace.Element, XpathQuery> getProxySubtagQuery(Namespace.Element subtag) {
    return ImmutablePair.of(subtag, new XpathQuery("/%s/%s[not(%s='true')]/%s",
        XpathQuery.RDF_TAG, ORE_PROXY, EDM_EUROPEANA_PROXY, subtag));
  }

  @Override
  public NormalizeActionResult normalize(RecordWrapper edmRecord) throws NormalizationException {

    // Find the Europeana proxy.
    final Document document = edmRecord.getAsDocument();
    final Element europeanaProxy = XmlUtil.getUniqueElement(EUROPEANA_PROXY, document);
    final Map<String, Element> normalizedTimespans = new HashMap<>();

    // Perform the two different kinds of normalizations
    final InternalNormalizationReport report = new InternalNormalizationReport();
    report.mergeWith(normalizeElements(document, europeanaProxy, DATE_PROPERTY_FIELDS,
        this::normalizeDateProperty, normalizedTimespans));
    report.mergeWith(normalizeElements(document, europeanaProxy, GENERIC_PROPERTY_FIELDS,
        this::normalizeGenericProperty, normalizedTimespans));

    // Done.
    return new NormalizeActionResult(RecordWrapper.create(document), report);
  }

  private InternalNormalizationReport normalizeElements(Document document, Element europeanaProxy,
      List<Pair<Namespace.Element, XpathQuery>> propertyFields,
      Function<String, DateNormalizationResult> normalizationFunction, Map<String, Element> normalizedTimespans)
      throws NormalizationException {
    final InternalNormalizationReport report = new InternalNormalizationReport();
    for (Pair<Namespace.Element, XpathQuery> query : propertyFields) {
      try {
        final List<Element> elements = XmlUtil.getAsElementList(query.getRight().execute(document));
        for (Element element : elements) {
          normalizeElement(document, element, query.getLeft(), europeanaProxy,
              normalizationFunction, report, normalizedTimespans);
        }
      } catch (XPathExpressionException e) {
        throw new NormalizationException("Xpath query issue: " + e.getMessage(), e);
      }
    }
    return report;
  }

  private void normalizeElement(Document document, Element element, Namespace.Element elementType,
      Element europeanaProxy, Function<String, DateNormalizationResult> normalizationFunction,
      InternalNormalizationReport report, Map<String, Element> normalizedTimespans) {

    // Apply the normalization. If nothing can be done, we return.
    final SourceLiteral sourceLiteral = new SourceLiteral(XmlUtil.getElementText(element),
        element.getAttributeNS(XML_LANG.namespace().getUri(), XML_LANG.elementName()));
    final DateNormalizationResult dateNormalizationResult = normalizationFunction.apply(sourceLiteral.text());
    if (dateNormalizationResult.getDateNormalizationResultStatus() == NO_MATCH) {
      log.debug("Normalization did not find a match");
      return;
    }

    // Preserve annotations in the URI as well as the preferred label.
    final String timespanIdText = hasRemovedBracketedAnnotation(dateNormalizationResult)
        ? sourceLiteral.text() : dateNormalizationResult.getEdtfDate().toString();
    final String timespanId = UriComponentsBuilder.newInstance().fragment(timespanIdText).toUriString();

    final Element timespanEntity = normalizedTimespans.computeIfAbsent(timespanId,
        uri -> appendTimespanEntity(document, dateNormalizationResult, sourceLiteral, uri));
    includeInTimeSpanEntity(timespanEntity, sourceLiteral.text(), sourceLiteral.languageTag());

    // Add a reference to the timespan to the Europeana proxy. All elements we're adding
    // go at the beginning of the proxy in a choice, so the order doesn't matter.
    final Element reference = XmlUtil.createElement(elementType, europeanaProxy, List.of());
    final String fullResourceName = XmlUtil.getPrefixedElementName(RDF_RESOURCE,
        reference.lookupPrefix(RDF_RESOURCE.namespace().getUri()));
    final Attr dcTermsIsPartOfResource = document.createAttributeNS(
        RDF_RESOURCE.namespace().getUri(), fullResourceName);
    dcTermsIsPartOfResource.setValue(timespanId);
    reference.setAttributeNode(dcTermsIsPartOfResource);

    // Update the report.
    report.increment(this.getClass().getSimpleName(), ConfidenceLevel.CERTAIN);
  }

  /**
   * Normalizer a property that is expected to be a date.
   *
   * @param input the date
   * @return the date normalization result
   */
  public DateNormalizationResult normalizeDateProperty(String input) {
    return normalizeProperty(input, normalizationOperationsInOrderDateProperty);
  }

  /**
   * Normalizer a property that is expected to be a generic property, so the process is more strict than properties that are
   * expected to be a date.
   *
   * @param input the date
   * @return the date normalization result
   */
  public DateNormalizationResult normalizeGenericProperty(String input) {
    return normalizeProperty(input, normalizationOperationsInOrderGenericProperty);
  }

  private DateNormalizationResult normalizeProperty(
      String input, final List<Function<String, DateNormalizationResult>> normalizationOperationsInOrder) {

    DateNormalizationResult dateNormalizationResult;
    String sanitizedInput = sanitizeCharacters(input);

    //Normalize trying operations in order
    dateNormalizationResult = normalizationOperationsInOrder
        .stream()
        .map(operation -> operation.apply(sanitizedInput))
        .filter(result -> result.getDateNormalizationResultStatus() == MATCHED)
        .findFirst()
        .orElse(DateNormalizationResult.getNoMatchResult(input));

    return dateNormalizationResult;
  }

  private DateNormalizationResult normalizeInput(List<DateExtractor> dateExtractors, String inputDate) {
    return dateExtractors.stream().map(dateExtractor -> dateExtractor.extractDateProperty(inputDate))
                         .filter(dateNormalizationResult -> dateNormalizationResult.getDateNormalizationResultStatus()
                             == MATCHED).findFirst()
                         .orElse(DateNormalizationResult.getNoMatchResult(inputDate));
  }

  private DateNormalizationResult normalizeInputGeneric(List<DateExtractor> dateExtractors, String input) {
    return dateExtractors.stream().map(dateExtractor -> dateExtractor.extractGenericProperty(input))
                         .filter(dateNormalizationResult -> dateNormalizationResult.getDateNormalizationResultStatus()
                             == MATCHED).findFirst()
                         .orElse(DateNormalizationResult.getNoMatchResult(input));
  }

  private DateNormalizationResult normalizeInputSanitized(List<DateExtractor> dateExtractors, String input,
      Function<String, SanitizedDate> sanitizeFunction, Predicate<SanitizeOperation> checkIfApproximateCleanOperationId,
      BiFunction<List<DateExtractor>, SanitizedDate, DateNormalizationResult> normalizeFunction) {
    final SanitizedDate sanitizedDate = sanitizeFunction.apply(input);
    DateNormalizationResult dateNormalizationResult = DateNormalizationResult.getNoMatchResult(input);
    if (sanitizedDate != null && StringUtils.isNotEmpty(sanitizedDate.sanitizedDateString())) {
      dateNormalizationResult = normalizeFunction.apply(dateExtractors, sanitizedDate);
      if (dateNormalizationResult.getDateNormalizationResultStatus() == MATCHED) {
        if (checkIfApproximateCleanOperationId.test(sanitizedDate.sanitizeOperation())) {
          dateNormalizationResult.getEdtfDate().addQualification(DateQualification.APPROXIMATE);
        }
        //Re-create result containing sanitization operation.
        dateNormalizationResult = new DateNormalizationResult(dateNormalizationResult, sanitizedDate.sanitizeOperation());
      }
    }
    return dateNormalizationResult;
  }

  /**
   * Cleans and normalizes specific characters.
   * <p>
   * Specifically, it will in order:
   *   <ul>
   *     <li>Trim the input</li>
   *     <li>Replace non-breaking spaces with normal spaces</li>
   *     <li>Replace en dash with a normal dash</li>
   *   </ul>
   * </p>
   *
   * @param input the string input
   * @return the normalized string
   */
  private static String sanitizeCharacters(String input) {
    String valTrim = input.trim();
    valTrim = valTrim.replace('\u00a0', ' '); // replace non-breaking spaces with normal spaces
    valTrim = valTrim.replace('\u2013', '-'); // replace en dash by normal dash
    return valTrim;
  }

  /**
   * Checks whether the successful sanitization removed an annotation in parentheses or square brackets.
   */
  private static boolean hasRemovedBracketedAnnotation(DateNormalizationResult result) {
    final SanitizeOperation operation = result.getSanitizeOperation();
    return operation == SanitizeOperation.STARTING_PARENTHESES
        || operation == SanitizeOperation.ENDING_PARENTHESES
        || operation == SanitizeOperation.ENDING_SQUARE_BRACKETS;
  }

  private Element appendTimespanEntity(Document document, DateNormalizationResult dateNormalizationResult,
      SourceLiteral sourceLiteral, String timespanId) {
    final AbstractEdtfDate edtfDate = dateNormalizationResult.getEdtfDate();

    //Check if element with the same id already exists, if so we need to remove it first.
    List<Element> elements = XmlUtil.getAsElementList(document.getDocumentElement()
                                                              .getElementsByTagNameNS(EDM_TIMESPAN.namespace().getUri(),
                                                                  EDM_TIMESPAN.elementName()));
    for (Element element : elements) {
      String aboutValue = element.getAttributeNS(RDF_ABOUT.namespace().getUri(), RDF_ABOUT.elementName());
      if (timespanId.equals(aboutValue)) {
        document.getDocumentElement().removeChild(element);
      }
    }

    // TODO: 09/08/2022 All the element prefixes below are searched first and if not found then the suggested prefix is added.
    //  When it does not exist in the root the namespace is added in the element itself.
    //  Should we be adding it in the root element of the document instead?
    // Create and add timespan element to document (RDF).
    final Element timeSpan = XmlUtil.createElement(EDM_TIMESPAN, document.getDocumentElement(),
        List.of(EDM_PROVIDED_CHO, EDM_AGENT, EDM_PLACE, EDM_WEB_RESOURCE, EDM_TIMESPAN));
    final String fullRdfAboutName = XmlUtil.getPrefixedElementName(RDF_ABOUT,
        document.getDocumentElement().lookupPrefix(RDF_ABOUT.namespace().getUri()));
    final Attr rdfAbout = document.createAttributeNS(RDF_ABOUT.namespace().getUri(), fullRdfAboutName);
    rdfAbout.setValue(timespanId);
    timeSpan.setAttributeNode(rdfAbout);

    // Create and add skosPrefLabel to timespan
    final Element skosPrefLabel = XmlUtil.createElement(SKOS_PREF_LABEL, timeSpan, null);
    final String prefLabelText;
    final String prefLabelLanguage;
    if (dateNormalizationResult.getDateNormalizationExtractorMatchId() == DCMI_PERIOD && StringUtils.isNotBlank(
        edtfDate.getLabel())) {
      prefLabelText = edtfDate.getLabel();
      prefLabelLanguage = sourceLiteral.languageTag();
    } else if (hasRemovedBracketedAnnotation(dateNormalizationResult)) {
      prefLabelText = sourceLiteral.text();
      prefLabelLanguage = sourceLiteral.languageTag();
    } else {
      prefLabelText = edtfDate.toString();
      prefLabelLanguage = "zxx";
    }
    skosPrefLabel.appendChild(document.createTextNode(prefLabelText));
    setLabelLanguage(skosPrefLabel, prefLabelLanguage);

    // Create and add skosNote elements to timespan in case of approximate or uncertain dates.
    if (edtfDate.getDateQualifications().contains(DateQualification.APPROXIMATE)) {
      final Element skosNote = XmlUtil.createElement(SKOS_NOTE, timeSpan, null);
      skosNote.appendChild(document.createTextNode("approximate"));
    }
    if (edtfDate.getDateQualifications().contains(DateQualification.UNCERTAIN)) {
      final Element skosNote = XmlUtil.createElement(SKOS_NOTE, timeSpan, null);
      skosNote.appendChild(document.createTextNode("uncertain"));
    }

    // Compute the date range and century range.
    final InstantEdtfDate firstDay = edtfDate.getFirstDay();
    final InstantEdtfDate lastDay = edtfDate.getLastDay();
    Integer startCentury = Optional.ofNullable(firstDay)
                                   .map(InstantEdtfDate::getCentury).orElse(null);
    Integer endCentury = Optional.ofNullable(lastDay)
                                 .map(InstantEdtfDate::getCentury).orElse(null);

    // Sanity check: It should normally not happen that both start and end century are null.
    if (startCentury == null && endCentury == null) {
      throw new IllegalStateException("Normalized date has no calculated boundaries: " + edtfDate);
    }
    if (startCentury == null) {
      startCentury = endCentury;
    } else if (endCentury == null) {
      endCentury = startCentury;
    }

    // Create century links only within the supported vocabulary range (1-21).
    final String fullResourceName = XmlUtil.getPrefixedElementName(RDF_RESOURCE,
        timeSpan.lookupPrefix(RDF_RESOURCE.namespace().getUri()));
    for (int century = Math.max(1, startCentury); century <= Math.min(MAX_VOCABULARY_CENTURY, endCentury); century++) {
      final Element dctermsIsPartOf = XmlUtil.createElement(DC_TERMS_IS_PART_OF, timeSpan, null);
      final Attr dctermsIsPartOfResource = document.createAttributeNS(RDF_RESOURCE.namespace().getUri(), fullResourceName);
      dctermsIsPartOfResource.setValue("http://data.europeana.eu/timespan/" + century);
      dctermsIsPartOf.setAttributeNode(dctermsIsPartOfResource);
    }

    // Create and add the begin and end.
    if (firstDay != null) {
      final Element edmBegin = XmlUtil.createElement(EDM_BEGIN, timeSpan, null);
      edmBegin.appendChild(document.createTextNode(firstDay.toString()));
    }
    if (lastDay != null) {
      final Element edmEnd = XmlUtil.createElement(EDM_END, timeSpan, null);
      edmEnd.appendChild(document.createTextNode(lastDay.toString()));
    }

    // Create and add skosNotation
    final Element skosNotation = XmlUtil.createElement(SKOS_NOTATION, timeSpan, null);
    final String fullNotationTypeName = XmlUtil.getPrefixedElementName(RDF_DATATYPE,
        timeSpan.lookupPrefix(RDF_DATATYPE.namespace().getUri()));
    final Attr skosNotationType = document.createAttributeNS(RDF_DATATYPE.namespace().getUri(), fullNotationTypeName);
    skosNotationType.setValue("http://id.loc.gov/datatypes/edtf/EDTF-level1");
    skosNotation.setAttributeNode(skosNotationType);
    skosNotation.appendChild(document.createTextNode(edtfDate.toString()));
    return timeSpan;
  }

  /**
   * Adds the original literal unless an existing label has the same text and language. An untagged literal also matches a
   * preferred label tagged as zxx.
   */
  void includeInTimeSpanEntity(Element timespanEntity, String originalValue, String languageTag) {
    final String language = StringUtils.defaultString(languageTag);
    for (Namespace.Element labelType : SKOS_LABELS) {
      final List<Element> labels = XmlUtil.getAsElementList(timespanEntity.getElementsByTagNameNS(
          labelType.namespace().getUri(), labelType.elementName()));
      for (Element label : labels) {
        final String labelLanguage = label.getAttributeNS(XML_LANG.namespace().getUri(), XML_LANG.elementName());
        final boolean languageMatches = language.equals(labelLanguage)
            || (labelType == SKOS_PREF_LABEL && "zxx".equals(labelLanguage) && language.isEmpty());
        if (languageMatches && originalValue.equals(XmlUtil.getElementText(label))) {
          return;
        }
      }
    }

    final Element hiddenLabel = XmlUtil.createElement(SKOS_HIDDEN_LABEL, timespanEntity, SKOS_LABELS);
    hiddenLabel.appendChild(timespanEntity.getOwnerDocument().createTextNode(originalValue));
    setLabelLanguage(hiddenLabel, language);
  }

  private static void setLabelLanguage(Element label, String language) {
    if (StringUtils.isNotEmpty(language)) {
      final String fullLangName = XmlUtil.getPrefixedElementName(XML_LANG,
          label.lookupPrefix(XML_LANG.namespace().getUri()));
      label.setAttributeNS(XML_LANG.namespace().getUri(), fullLangName, language);
    }
  }

  /**
   * Unprocessed provider text and its original language tag, captured before extraction.
   */
  private record SourceLiteral(String text, String languageTag) {

  }
}
