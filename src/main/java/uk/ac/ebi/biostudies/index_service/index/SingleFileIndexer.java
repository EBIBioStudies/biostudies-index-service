package uk.ac.ebi.biostudies.index_service.index;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.model.FileDocumentField;

/**
 * Indexes individual file metadata into Lucene for both direct submission files and FileList
 * entries.
 *
 * <p>Creates Lucene documents via {@link FileDocumentFactory}, updates the index with unique
 * document IDs (accno-position), and extracts section information for faceting. Handles both direct
 * file attachments and files discovered through file list processing pipelines [file:2].
 *
 * <p>Thread-safe for concurrent invocation from virtual thread pools. All mutable state is passed
 * via {@link FileIndexingContext} which provides:
 *
 * <ul>
 *   <li>Lucene {@link IndexWriter}
 *   <li>File processing counter
 *   <li>File attribute columns list
 *   <li>File-containing sections set
 *   <li>Searchable file metadata set
 *   <li>Indexing error flag
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleFileIndexer {

  private final FileDocumentFactory fileDocumentFactory;

  /**
   * Indexes a single file using the provided indexing context.
   *
   * <p>Builds a Lucene document with submission accession, file position, and metadata extracted
   * from the file JSON node. Updates the index atomically and updates context state including:
   *
   * <ul>
   *   <li>File processing counter (increments by 1)
   *   <li>File columns (adds discovered attributes)
   *   <li>Sections with files (adds section name)
   *   <li>Searchable metadata (adds fileName:md5 pairs)
   *   <li>Error flag (sets true on failures)
   * </ul>
   *
   * @param accession submission accession number (e.g., "S-BIAD2077")
   * @param context thread-safe indexing context with writer and mutable state
   * @param parent JSON node of parent section/fileList containing this file
   * @param fileNode JSON metadata for this individual file
   * @throws IOException if Lucene indexing fails
   */
  public void indexFile(
      String accession, FileIndexingContext context, JsonNode parent, JsonNode fileNode)
      throws IOException {

    long position = context.getFileCounter().getAndIncrement();
    String docId = accession + "-" + position;
    log.debug("Indexing file {} - docId: {}", accession, docId);
    String fileName = "unknown";

    try {
      // Build Lucene document with all file metadata
      Document doc = fileDocumentFactory.buildFileDocument(accession, context, fileNode, parent);

      fileName = doc.get(FileDocumentField.NAME.getName());

      // Atomic index update using unique document ID
      context.getIndexWriter().updateDocument(new Term(FileDocumentField.ID.getName(), docId), doc);

      // Extract stored section for faceting (first stored field only)
      String sectionName = extractStoredSection(doc);
      if (sectionName != null) {
        context.getSectionsWithFiles().add(sectionName);
      }

    } catch (Exception ex) {
      log.error("Failed to index file {} ({}): {}", docId, fileName, ex.getMessage(), ex);
      context.getHasIndexingError().set(true);
      throw new IOException("File indexing failed for " + docId, ex);
    }
  }

  /**
   * Extracts the first stored section field value from a Lucene document.
   *
   * <p>BioStudies UI requires stored section names separate from search-indexed versions.
   *
   * @param doc Lucene document containing section fields
   * @return stored section name, or null if none found
   */
  private String extractStoredSection(Document doc) {
    IndexableField[] sectionFields = doc.getFields(FileDocumentField.SECTION.getName());
    if (sectionFields == null) {
      return null;
    }

    for (IndexableField field : sectionFields) {
      if (field.fieldType().stored() && field.stringValue() != null) {
        return field.stringValue();
      }
    }
    return null;
  }
}
