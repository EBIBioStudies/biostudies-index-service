package uk.ac.ebi.biostudies.index_service.search.mappers;

import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;
import uk.ac.ebi.biostudies.index_service.model.IndexedSubmission;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;
import uk.ac.ebi.biostudies.index_service.registry.model.SubmissionField;
import uk.ac.ebi.biostudies.index_service.search.engine.DocumentMapper;

@Component
public class IndexedSubmissionMapper implements DocumentMapper<IndexedSubmission> {

  /**
   * Converts a Lucene document to a domain-specific DTO.
   *
   * @param document the Lucene document to map
   * @return the mapped DTO instance
   * @throws NullPointerException  if document is null
   * @throws IllegalStateException if required fields are missing or invalid
   */
  @Override
  public IndexedSubmission toDto(Document document) {
    IndexedSubmission submission = new IndexedSubmission();
    submission.setAccession(document.get(SubmissionField.ACCESSION.getName()));
    submission.setAccess(document.get(SubmissionField.ACCESS.getName()));
    submission.setType(document.get(SubmissionField.TYPE.getName()));
    submission.setStorageMode(document.get(SubmissionField.STORAGE_MODE.getName()));
    return submission;
  }
}
