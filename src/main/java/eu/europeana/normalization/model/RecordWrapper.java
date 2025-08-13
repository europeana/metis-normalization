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
   * @param record The record as String.
   * @return An instance.
   */
  static RecordWrapper create(String record) {
    return new RecordWrapper() {
      @Override
      public String getAsString() {
        return record;
      }

      @Override
      public Document getAsDocument() throws NormalizationException {
        return RecordWrapper.stringToDocument(record);
      }

      @Override
      public RDF getAsRDF() throws NormalizationException {
        return RecordWrapper.stringToRdf(record);
      }
    };
  }

  /**
   * Create an instance based on a Document.
   *
   * @param record The record as Document.
   * @return An instance.
   */
  static RecordWrapper create(Document record) {
    return new RecordWrapper() {
      @Override
      public String getAsString() throws NormalizationException {
        return RecordWrapper.documentToString(record);
      }

      @Override
      public Document getAsDocument() {
        return record;
      }

      @Override
      public RDF getAsRDF() throws NormalizationException {
        return RecordWrapper.stringToRdf(RecordWrapper.documentToString(record));
      }
    };
  }

  /**
   * Create an instance based on an RDF.
   *
   * @param record The record as RDF.
   * @return An instance.
   */
  static RecordWrapper create(RDF record) {
    return new RecordWrapper() {

      @Override
      public String getAsString() throws NormalizationException {
        return RecordWrapper.rdfToString(record);
      }

      @Override
      public Document getAsDocument() throws NormalizationException {
        return RecordWrapper.stringToDocument(RecordWrapper.rdfToString(record));
      }

      @Override
      public RDF getAsRDF() {
        return record;
      }
    };
  }

  private static Document stringToDocument(String record) throws NormalizationException {
    try {
      return XmlUtil.parseDom(new StringReader(record));
    } catch (XmlException | RuntimeException e) {
      throw new NormalizationException("Issue converting record String to Document", e);
    }
  }

  private static String documentToString(Document record) throws NormalizationException {
    try {
      return XmlUtil.writeDomToString(record);
    } catch (XmlException | RuntimeException e) {
      throw new NormalizationException("Issue converting record Document to String", e);
    }
  }

  private static RDF stringToRdf(String record) throws NormalizationException {
    try {
      return new RdfConversionUtils().convertStringToRdf(record);
    } catch (SerializationException | RuntimeException e) {
      throw new NormalizationException("Issue converting record String to RDF", e);
    }
  }

  private static String rdfToString(RDF record) throws NormalizationException {
    try {
      return new RdfConversionUtils().convertRdfToString(record);
    } catch (SerializationException | RuntimeException e) {
      throw new NormalizationException("Issue converting record RDF to String", e);
    }
  }
}
