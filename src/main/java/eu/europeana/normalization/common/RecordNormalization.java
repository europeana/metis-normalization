package eu.europeana.normalization.common;

import org.w3c.dom.Document;
import eu.europeana.normalization.model.NormalizationReport;


public interface RecordNormalization {

  NormalizationReport normalize(Document edm);
}
