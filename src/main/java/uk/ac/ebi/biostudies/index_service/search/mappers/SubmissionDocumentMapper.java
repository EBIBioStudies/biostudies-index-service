package uk.ac.ebi.biostudies.index_service.search.mappers;

import java.time.LocalDate;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.registry.model.SubmissionField;
import uk.ac.ebi.biostudies.index_service.search.engine.DocumentMapper;
import uk.ac.ebi.biostudies.index_service.search.searchers.SubmissionSearchHit;

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
        isPublic(document),
        document.get(SubmissionField.CONTENT.getName()));
  }

  /**
   * Checks if document represents a public submission.
   *
   * <p>Parses {@link SubmissionField#ACCESS} field (space-separated tags) and returns true if
   * contains {@link Constants#PUBLIC} (case-insensitive).
   *
   * @param document Lucene document to check
   * @return true if public (contains "public" tag), false otherwise
   */
  private boolean isPublic(Document document) {
    String access = document.get(SubmissionField.ACCESS.getName());
    if (access == null || access.trim().isEmpty()) {
      return false;
    }

    // Normalize to lowercase and split by whitespace
    String[] accessTags = access.toLowerCase().trim().split("\\s+");
    String publicTag = Constants.PUBLIC.toLowerCase();

    return Arrays.asList(accessTags).contains(publicTag);
  }
}
