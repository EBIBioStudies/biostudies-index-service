package uk.ac.ebi.biostudies.index_service.registry.model;

import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.document.FieldType;

public class LuceneFieldTypes {

  public static final FieldType NOT_ANALYZED_STORED = createNotAnalyzedStored();

  private static FieldType createNotAnalyzedStored() {
    FieldType type = new FieldType();
    type.setIndexOptions(IndexOptions.DOCS);
    type.setTokenized(false);
    type.setStored(true);
    type.setOmitNorms(true);
    type.freeze();
    return type;
  }
}
