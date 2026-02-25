package uk.ac.ebi.biostudies.index_service.search.mappers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.model.IndexedSubmission;
import uk.ac.ebi.biostudies.index_service.registry.model.SubmissionField;
import uk.ac.ebi.biostudies.index_service.search.engine.DocumentMapper;

@Component
public class IndexedSubmissionMapper implements DocumentMapper<IndexedSubmission> {

  /**
   * Converts a Lucene document to a domain-specific DTO.
   *
   * @param document the Lucene document to map
   * @return the mapped DTO instance
   * @throws NullPointerException if document is null
   * @throws IllegalStateException if required fields are missing or invalid
   */
  @Override
  public IndexedSubmission toDto(Document document) {
    IndexedSubmission submission = new IndexedSubmission();
    submission.setId(document.get(SubmissionField.ID.getName()));
    submission.setAccession(document.get(SubmissionField.ACCESSION.getName()));
    submission.setAccess(document.get(SubmissionField.ACCESS.getName()));
    submission.setType(document.get(SubmissionField.TYPE.getName()));
    submission.setRelPath(document.get(SubmissionField.REL_PATH.getName()));
    submission.setStorageMode(document.get(SubmissionField.STORAGE_MODE.getName()));
    String sectionsWithFilesStr = document.get(SubmissionField.SECTIONS_WITH_FILES.getName());
    List<String> sectionsWithFiles =
        sectionsWithFilesStr != null
            ? Arrays.asList(sectionsWithFilesStr.split(" "))
            : Collections.emptyList();
    submission.setSectionsWithFiles(sectionsWithFiles);
    String fileAttributesNamesStr = document.get(SubmissionField.FILE_ATTRIBUTE_NAMES.getName());
    List<String> fileAttributesNames =
        fileAttributesNamesStr != null
            ? Arrays.asList(fileAttributesNamesStr.split("\\|"))
            : Collections.emptyList();
    submission.setFileAttributesNames(fileAttributesNames);
    submission.setHasFileIndexingError(
        document.get(SubmissionField.HAS_FILE_PARSING_ERROR.getName()) != null);
    submission.setReleaseTime(Long.parseLong(document.get(SubmissionField.RELEASE_TIME.getName())));
    submission.setModificationTime(
        Long.parseLong(document.get(SubmissionField.MODIFICATION_TIME.getName())));
    submission.setViews(Integer.parseInt(document.get(SubmissionField.VIEWS.getName())));
    submission.setNumberOfFiles(Integer.parseInt(document.get(SubmissionField.FILES.getName())));
    submission.setNumberOfLinks(Integer.parseInt(document.get(SubmissionField.LINKS.getName())));

    return submission;
  }
}
