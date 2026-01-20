package uk.ac.ebi.biostudies.index_service.index;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;
import uk.ac.ebi.biostudies.index_service.model.FileDocumentField;

/**
 * Orchestrates file indexing for BioStudies submissions. Processes direct files and FileLists,
 * manages cleanup, and populates submission metadata via context [file:2].
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilesIndexer {

  private final FileDocumentFactory fileDocumentFactory;
  private final SingleFileIndexer singleFileIndexer;
  private final FileListsIndexer fileListsIndexer;

  /**
   * Indexes all submission files and populates context valueMap.
   *
   * <p>Workflow: cleanup → direct files → FileLists → valueMap population. Merges discovered
   * columns with provided attributeColumns.
   *
   * @param submissionMetadata submission with file/fileList JSON
   * @param writer Lucene IndexWriter
   * @param attributeColumns columns to merge with discovered file columns
   * @param removeFileDocuments delete existing files first
   * @return context with populated valueMap, counters, and columns
   * @throws IOException on indexing failures
   */
  public FileIndexingContext indexSubmissionFiles(
      ExtendedSubmissionMetadata submissionMetadata,
      IndexWriter writer,
      Set<String> attributeColumns,
      boolean removeFileDocuments)
      throws IOException {

    Objects.requireNonNull(submissionMetadata, "Submission metadata cannot be null");
    Objects.requireNonNull(writer, "IndexWriter cannot be null");

    log.info(
        "Indexing files for {} (remove existing: {})",
        submissionMetadata.getAccNo(),
        removeFileDocuments);

    FileIndexingContext context = createIndexingContext(writer);

    try {
      if (removeFileDocuments) {
        removeFileDocuments(writer, submissionMetadata.getAccNo());
      }

      indexDirectFiles(submissionMetadata, context);
      fileListsIndexer.indexFileLists(submissionMetadata, context);

      populateValueMapAndColumns(context, attributeColumns);

      log.info(
          "File indexing complete for {}: {} files, {} sections",
          submissionMetadata.getAccNo(),
          context.getFileCounter().get(),
          context.getSectionsWithFiles().size());

      return context;

    } catch (Exception e) {
      log.error(
          "File indexing failed for {}: {}", submissionMetadata.getAccNo(), e.getMessage(), e);
      context.getHasIndexingError().set(true);
      return context;
    }
  }

  private FileIndexingContext createIndexingContext(IndexWriter writer) {
    return FileIndexingContext.builder()
        .indexWriter(writer)
        .fileCounter(new AtomicLong())
        .fileColumns(Collections.synchronizedSet(new HashSet<>()))
        .sectionsWithFiles(ConcurrentHashMap.newKeySet())
        .searchableFileMetadata(ConcurrentHashMap.newKeySet())
        .hasIndexingError(new AtomicBoolean(false))
        .valueMap(new HashMap<>())
        .build();
  }

  private void populateValueMapAndColumns(
      FileIndexingContext context, Set<String> attributeColumns) {
    Objects.requireNonNull(context.getValueMap(), "valueMap cannot be null");

    List<String> fileColumns = reorderFileColumns(new ArrayList<>(context.getFileColumns()));
    attributeColumns.addAll(fileColumns);

    if (!context.getSectionsWithFiles().isEmpty()) {
      context
          .getValueMap()
          .put(Constants.SECTIONS_WITH_FILES, String.join(" ", context.getSectionsWithFiles()));
    }

    context.getValueMap().put(Constants.FILES, context.getFileCounter().longValue());
    context
        .getValueMap()
        .put(Constants.FILE_ATT_KEY_VALUE, String.join(" ", context.getSearchableFileMetadata()));

    if (context.getHasIndexingError().get()) {
      context.getValueMap().put(Constants.HAS_FILE_PARSING_ERROR, "true");
    }
  }

  private List<String> reorderFileColumns(List<String> fileColumns) {
    if (!fileColumns.contains(Constants.SECTION)) {
      return fileColumns;
    }

    fileColumns.remove(Constants.SECTION);
    fileColumns.add(0, Constants.SECTION);
    return fileColumns;
  }

  private void indexDirectFiles(
      ExtendedSubmissionMetadata submissionMetadata, FileIndexingContext context)
      throws IOException {
    List<JsonNode> parentsWithFiles =
        findParentsWithFiles(submissionMetadata.getRawSubmissionJson());

    log.debug("Found {} direct file sections", parentsWithFiles.size());

    for (JsonNode parent : parentsWithFiles) {
      indexFilesUnderParent(submissionMetadata.getAccNo(), context, parent, parent);
    }
  }

  private List<JsonNode> findParentsWithFiles(JsonNode submission) {
    return submission.findParents("files").stream()
        .filter(Objects::nonNull)
        .filter(parent -> parent.has("files") && !parent.get("files").isEmpty())
        .toList();
  }

  private void indexFilesUnderParent(
      String accession, FileIndexingContext context, JsonNode parentSection, JsonNode nodeWithFiles)
      throws IOException {
    JsonNode filesNode = nodeWithFiles.get("files");
    if (filesNode == null || !filesNode.isArray()) {
      return;
    }

    for (JsonNode fNode : filesNode) {
      List<JsonNode> fileNodes = resolveFileNodes(fNode);
      for (JsonNode fileNode : fileNodes) {
        if (shouldIndexNode(parentSection)) {
          singleFileIndexer.indexFile(accession, context, parentSection, fileNode);
        }
      }
    }
  }

  private boolean shouldIndexNode(JsonNode parent) {
    String className = parent.has("_class") ? parent.get("_class").asText() : "";
    return !className.endsWith("DocFileList");
  }

  private List<JsonNode> resolveFileNodes(JsonNode fNode) {
    if (fNode.isArray()) {
      List<JsonNode> result = new ArrayList<>();
      fNode.forEach(result::add);
      return result;
    }

    if (fNode.has("files") && fNode.get("files").isArray()) {
      List<JsonNode> result = new ArrayList<>();
      fNode.get("files").forEach(result::add);
      return result;
    }

    return Collections.singletonList(fNode);
  }

  void removeFileDocuments(IndexWriter writer, String accession) {
    try {
      QueryParser parser =
          new QueryParser(FileDocumentField.OWNER.getName(), new KeywordAnalyzer());
      Query query = parser.parse(FileDocumentField.OWNER.getName() + ":" + accession);
      writer.deleteDocuments(query);
      log.debug("Removed existing files for {}", accession);
    } catch (Exception e) {
      log.error("Failed to remove existing files for {}", accession, e);
    }
  }
}
