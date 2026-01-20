package uk.ac.ebi.biostudies.index_service.index;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

/**
 * High-level entry point for indexing a BioStudies submission.
 *
 * <p>This component orchestrates:
 *
 * <ul>
 *   <li>Parsing submission-level metadata into a value map
 *   <li>Indexing all related files via {@link FilesIndexer}
 *   <li>Aggregating indexing state into an {@link IndexingResult}
 * </ul>
 *
 * <p>File indexing errors are captured in the returned result and logged, rather than propagated to
 * callers as exceptions.
 */
@Slf4j
@Component
public class SubmissionIndexer {

  private final SubmissionParser submissionParser;
  private final FilesIndexer filesIndexer;
  private final SubmissionDocumentCreator submissionDocumentCreator;
  private final IndexingTransactionManager indexingTransactionManager;

  public SubmissionIndexer(
      SubmissionParser submissionParser,
      FilesIndexer filesIndexer,
      SubmissionDocumentCreator submissionDocumentCreator,
      IndexingTransactionManager indexingTransactionManager) {
    this.submissionParser = submissionParser;
    this.filesIndexer = filesIndexer;
    this.submissionDocumentCreator = submissionDocumentCreator;
    this.indexingTransactionManager = indexingTransactionManager;
  }

  /**
   * Indexes a single submission and all its associated files.
   *
   * <p>The workflow is:
   *
   * <ol>
   *   <li>Parse submission JSON into a value map using {@link SubmissionParser}
   *   <li>Index file metadata (direct files + FileLists) via {@link FilesIndexer}
   *   <li>Collect discovered file columns and file indexing context
   *   <li>Return an {@link IndexingResult} summarizing the outcome
   * </ol>
   *
   * @param submissionMetadata submission to index, including accession and raw JSON
   * @param removeFileDocuments whether existing file documents should be removed before indexing
   * @param commit should process commit after finishing or delegate
   */
  public void indexOne(
      ExtendedSubmissionMetadata submissionMetadata, boolean removeFileDocuments, boolean commit)
      throws IOException {
    indexWithoutCommit(submissionMetadata, removeFileDocuments);
    if (commit) {
      indexingTransactionManager.commit();
    }
  }

  /**
   * Indexes a single submission and all its associated files without commiting to the lucene
   * indexes.
   *
   * <p>The workflow is:
   *
   * <ol>
   *   <li>Parse submission JSON into a value map using {@link SubmissionParser}
   *   <li>Index file metadata (direct files + FileLists) via {@link FilesIndexer}
   *   <li>Collect discovered file columns and file indexing context
   * </ol>
   *
   * @param submissionMetadata submission to index, including accession and raw JSON
   * @param removeFileDocuments whether existing file documents should be removed before indexing
   */
  public void indexWithoutCommit(
      ExtendedSubmissionMetadata submissionMetadata, boolean removeFileDocuments)
      throws IOException {
    Objects.requireNonNull(submissionMetadata, "Submission metadata cannot be null");
    String accNo = submissionMetadata.getAccNo();

    log.info("Indexing submission {}", submissionMetadata.getAccNo());

    Map<String, Object> valueMap;
    FileIndexingContext fileContext;
    boolean hasErrors;
    Set<String> columnSet = new LinkedHashSet<>();

    try {
      // Parse submission-level values
      valueMap = submissionParser.parseSubmission(submissionMetadata.getRawSubmissionJson());

      // Index files and collect file-level indexing state
      IndexWriter filesIndexWriter = indexingTransactionManager.getFilesIndexWriter();

      fileContext =
          filesIndexer.indexSubmissionFiles(
              submissionMetadata, filesIndexWriter, columnSet, removeFileDocuments);

      valueMap.putAll(fileContext.getValueMap());
      boolean appended = appendFileAttributesToContent(valueMap);
      if (!appended) {
        log.warn("Failed to append file attributes to content");
      }

      valueMap.put(Constants.FILE_ATTRIBUTE_NAMES, columnSet);
      Document indexedDocument = submissionDocumentCreator.createSubmissionDocument(valueMap);

      indexingTransactionManager.updateSubmissionDocument(indexedDocument, accNo);
      // TODO: Index extracted links

      hasErrors = fileContext.getHasIndexingError().get();

    } catch (IOException ioe) {
      log.error("IO failure indexing {}: {}", accNo, ioe.getMessage(), ioe);
      throw ioe;
    } catch (Exception e) {
      log.error("Unexpected failure indexing {}: {}", accNo, e.getMessage(), e);
      throw e;
    }

    log.info(
        "Indexing {} complete: files={}, errors={}",
        submissionMetadata.getAccNo(),
        fileContext.getFileCounter().get(),
        hasErrors);
  }

  // Deletes a submission from the main and related indexes
  public void deleteSubmission(String accNo) throws ParseException, IOException {
    indexingTransactionManager.deleteSubmission(accNo, filesIndexer);
    // TODO: searchService.clearStatsCache();
  }

  /**
   * Appends file attribute value to the CONTENT field if both exist and CONTENT is a String.
   *
   * @param valueMap submission values to modify
   * @return true if successfully appended, false otherwise
   */
  private boolean appendFileAttributesToContent(Map<String, Object> valueMap) {
    Object content = valueMap.get(Constants.CONTENT);
    Object fileAttrValue = valueMap.get(Constants.FILE_ATT_KEY_VALUE);

    // Validate prerequisites
    if (!(content instanceof String contentStr) || fileAttrValue == null) {
      return false;
    }

    String fileAttrStr = fileAttrValue.toString().trim();

    // Skip if file attribute is empty
    if (fileAttrStr.isEmpty()) {
      return false;
    }

    // Append with consistent spacing
    valueMap.put(Constants.CONTENT, contentStr + " " + fileAttrStr);
    return true;
  }
}
