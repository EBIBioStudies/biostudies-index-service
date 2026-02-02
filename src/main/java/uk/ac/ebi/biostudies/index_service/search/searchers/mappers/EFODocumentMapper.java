package uk.ac.ebi.biostudies.index_service.search.searchers.mappers;

import java.util.List;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.search.engine.DocumentMapper;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearchHit;

@Component
public class EFODocumentMapper implements DocumentMapper<EFOSearchHit> {

  /**
   * Converts a Lucene document to a domain-specific DTO.
   *
   * @param document the Lucene document to map
   * @return the mapped DTO instance
   * @throws NullPointerException if document is null
   * @throws IllegalStateException if required fields are missing or invalid
   */
  @Override
  public EFOSearchHit toDto(Document document) {
    if (document == null) {
      throw new IllegalStateException("Document cannot be null");
    }
    String efoID = document.get(EFOField.EFO_ID.getFieldName());
    String term = document.get(EFOField.TERM.getFieldName());
    String child = document.get(EFOField.CHILDREN.getFieldName());
    String altTerm = document.get(EFOField.ALTERNATIVE_TERMS.getFieldName());
    List<String> synonyms = List.of(document.getValues(EFOField.QE_TERM.getFieldName()));
    List<String> efoTerms = List.of(document.getValues(EFOField.QE_EFO.getFieldName()));

    return new EFOSearchHit(efoID, term, child, altTerm, synonyms, efoTerms);
  }
}
