package eu.europeana.normalization.model;

import eu.europeana.metis.schema.convert.RdfConversionUtils;
import eu.europeana.metis.schema.convert.SerializationException;
import eu.europeana.metis.schema.jibx.RDF;
import eu.europeana.normalization.util.NormalizationException;
import eu.europeana.normalization.util.XmlException;
import eu.europeana.normalization.util.XmlUtil;
import java.io.StringReader;
import org.w3c.dom.Document;

/**
 * <p>This class allows for interoperability between the various formats that a record can be
 * represented with. Note that these instances are not expected to synchronize changes. I.e., if
 * changes are made to one of the representations that this wrapper serves, the wrapper should be
 * considered out of date and a new one should be created if needed.
 * </p>
 * <p>
 * Note that this class is expected to be temporary: it is designed to be in place only until all
 * normalization actions support RDF objects instead of on Document objects.
 * </p>
 */
public interface RecordWrapper {

  /**
   * Return the record as a String.
   *
   * @return The record as a String.
   * @throws NormalizationException If there were conversion issues.
   */
  String getAsString() throws NormalizationException;

  /**
   * Return the record as Document.
   *
   * @return The record as Document.
   * @throws NormalizationException If there were conversion issues.
   */
  Document getAsDocument() throws NormalizationException;

  /**
   * Return the record as RDF.
   *
   * @return The record as RDF.
   * @throws NormalizationException If there were conversion issues.
   */
  RDF getAsRDF() throws NormalizationException;

  /**
   * Create an instance based on a string record.
   *
   * @param edmRecord The record as String.
   * @return An instance.
   */
  static RecordWrapper create(String edmRecord) {
    return new RecordWrapper() {
      @Override
      public String getAsString() {
        return edmRecord;
      }

      @Override
      public Document getAsDocument() throws NormalizationException {
        return stringToDocument(edmRecord);
      }

      @Override
      public RDF getAsRDF() throws NormalizationException {
        return stringToRdf(edmRecord);
      }
    };
  }

  /**
   * Create an instance based on a Document.
   *
   * @param edmRecord The record as Document.
   * @return An instance.
   */
  static RecordWrapper create(Document edmRecord) {
    return new RecordWrapper() {
      @Override
      public String getAsString() throws NormalizationException {
        return documentToString(edmRecord);
      }

      @Override
      public Document getAsDocument() {
        return edmRecord;
      }

      @Override
      public RDF getAsRDF() throws NormalizationException {
        return stringToRdf(documentToString(edmRecord));
      }
    };
  }

  /**
   * Create an instance based on an RDF.
   *
   * @param edmRecord The record as RDF.
   * @return An instance.
   */
  static RecordWrapper create(RDF edmRecord) {
    return new RecordWrapper() {

      @Override
      public String getAsString() throws NormalizationException {
        return rdfToString(edmRecord);
      }

      @Override
      public Document getAsDocument() throws NormalizationException {
        return stringToDocument(rdfToString(edmRecord));
      }

      @Override
      public RDF getAsRDF() {
        return edmRecord;
      }
    };
  }

  /**
   * Convert a String to a Document.
   *
   * @param edmRecord The record to convert.
   * @return The converted record.
   * @throws NormalizationException In case there were conversion issues.
   */
  private static Document stringToDocument(String edmRecord) throws NormalizationException {
    try {
      return XmlUtil.parseDom(new StringReader(edmRecord));
    } catch (XmlException | RuntimeException e) {
      throw new NormalizationException("Issue converting record String to Document", e);
    }
  }

  /**
   * Convert a Document to a String.
   *
   * @param edmRecord The record to convert.
   * @return The converted record.
   * @throws NormalizationException In case there were conversion issues.
   */
  private static String documentToString(Document edmRecord) throws NormalizationException {
    try {
      return XmlUtil.writeDomToString(edmRecord);
    } catch (XmlException | RuntimeException e) {
      throw new NormalizationException("Issue converting record Document to String", e);
    }
  }

  /**
   * Convert a String to an RDF.
   *
   * @param edmRecord The record to convert.
   * @return The converted record.
   * @throws NormalizationException In case there were conversion issues.
   */
  private static RDF stringToRdf(String edmRecord) throws NormalizationException {
    try {
      return new RdfConversionUtils().convertStringToRdf(edmRecord);
    } catch (SerializationException | RuntimeException e) {
      throw new NormalizationException("Issue converting record String to RDF", e);
    }
  }

  /**
   * Convert an RDF to a String.
   *
   * @param edmRecord The record to convert.
   * @return The converted record.
   * @throws NormalizationException In case there were conversion issues.
   */
  private static String rdfToString(RDF edmRecord) throws NormalizationException {
    try {
      return new RdfConversionUtils().convertRdfToString(edmRecord);
    } catch (SerializationException | RuntimeException e) {
      throw new NormalizationException("Issue converting record RDF to String", e);
    }
  }
}
