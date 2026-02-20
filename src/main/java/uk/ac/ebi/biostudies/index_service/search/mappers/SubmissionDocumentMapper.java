package uk.ac.ebi.biostudies.index_service.search.mappers;

import java.time.LocalDate;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.registry.model.SubmissionField;
import uk.ac.ebi.biostudies.index_service.search.searchers.SubmissionSearchHit;
import uk.ac.ebi.biostudies.index_service.search.engine.DocumentMapper;

@Component
public class SubmissionDocumentMapper implements DocumentMapper<SubmissionSearchHit> {

  /**
   * Converts a Lucene document to a domain-specific DTO.
   *
   * @param document the Lucene document to map
   * @return the mapped DTO instance
   * @throws NullPointerException if document is null
   * @throws IllegalStateException if required fields are missing or invalid
   */
  @Override
  public SubmissionSearchHit toDto(Document document) {
    return new SubmissionSearchHit(
        document.get(SubmissionField.ACCESSION.getName()),
        document.get(SubmissionField.TYPE.getName()),
        document.get(SubmissionField.TITLE.getName()),
        document.get(SubmissionField.AUTHOR.getName()),
        Integer.parseInt(document.get(SubmissionField.LINKS.getName())),
        Integer.parseInt(document.get(SubmissionField.FILES.getName())),
        LocalDate.parse(document.get(SubmissionField.RELEASE_DATE.getName())),
        Integer.parseInt(document.get(SubmissionField.VIEWS.getName())),
        Boolean.parseBoolean(document.get(SubmissionField.IS_PUBLIC.getName())),
        document.get(SubmissionField.CONTENT.getName()));
  }
}
