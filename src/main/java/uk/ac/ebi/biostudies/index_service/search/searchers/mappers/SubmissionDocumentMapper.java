package uk.ac.ebi.biostudies.index_service.search.searchers.mappers;

import java.time.LocalDate;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.search.engine.SubmissionSearchHit;
import uk.ac.ebi.biostudies.index_service.search.searchers.DocumentMapper;

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
        document.get("accession"),
        document.get("type"),
        document.get("title"),
        document.get("author"),
        Integer.parseInt(document.get("links")),
        Integer.parseInt(document.get("files")),
        LocalDate.parse(document.get("release_date")),
        Integer.parseInt(document.get("views")),
        Boolean.parseBoolean(document.get("isPublic")),
        document.get("content"));
  }
}
