package uk.ac.ebi.biostudies.index_service.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.client.FileListHttpClient;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Identifies all File Lists in a BioStudies submission and indexes referenced file metadata using
 * virtual threads and batch processing for optimal performance [file:2].
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileListsIndexer {

  private static final String FILE_LIST_PROPERTY = "fileList";
  private static final String FILE_LIST_FILE_NAME_PROPERTY = "fileName";
  private static final String FILE_LIST_FILE_URL_PROPERTY = "filesUrl";
  private static final int FILE_BATCH_SIZE = 250;

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final FileListHttpClient fileListClient;
  private final SingleFileIndexer singleFileIndexer;

  /**
   * Gracefully shuts down the virtual thread executor on application shutdown.
   */
  @PreDestroy
  private void shutdown() {
    log.debug("Shutting down FileListsIndexer executor");
    executor.close();
  }

  /**
   * Indexes all file lists found in the submission using the provided indexing context.
   *
   * <p>Locates fileList sections, fetches metadata concurrently, partitions files into batches,
   * and delegates individual file indexing to {@link SingleFileIndexer}. All mutable state
   * (counters, columns, sections, metadata) is managed through the context.</p>
   *
   * @param submissionMetadata submission JSON containing fileList references
   * @param context thread-safe indexing context with writer and mutable state
   * @throws NullPointerException if submissionMetadata or context is null
   */
  public void indexFileLists(
      ExtendedSubmissionMetadata submissionMetadata,
      FileIndexingContext context) {

    Objects.requireNonNull(submissionMetadata, "Submission metadata cannot be null");
    Objects.requireNonNull(context, "Indexing context cannot be null");

    log.info("Indexing file lists for submission {} ({} files so far)",
        submissionMetadata.getAccNo(), context.getFileCounter().get());

    List<JsonNode> fileListSections = findFileListSections(submissionMetadata.getRawSubmissionJson());
    log.debug("Found {} file list sections in submission {}",
        fileListSections.size(), submissionMetadata.getAccNo());

    processFileListSections(fileListSections, submissionMetadata.getAccNo(), context);
  }

  /**
   * Finds all submission sections containing valid fileList objects.
   *
   * <p>Only sections with fileList.fileName are included. Returns empty list if none found.</p>
   *
   * @param json the submission JSON document to search for file list sections
   * @return list of all sections containing valid file lists, empty list if none found
   */
  public List<JsonNode> findFileListSections(JsonNode json) {
    List<JsonNode> fileListSections = new ArrayList<>();

    List<JsonNode> fileListParentSections = json.findParents(FILE_LIST_PROPERTY);
    for (JsonNode subSection : fileListParentSections) {
      JsonNode fileList = subSection.get(FILE_LIST_PROPERTY);
      if (fileList != null && fileList.has(FILE_LIST_FILE_NAME_PROPERTY)) {
        fileListSections.add(subSection);
      }
    }
    return fileListSections;
  }

  /**
   * Processes all file list sections concurrently using virtual threads.
   */
  private void processFileListSections(
      List<JsonNode> fileListSections,
      String submissionAccession,
      FileIndexingContext context) {

    fileListSections.parallelStream()
        .map(this::extractFileNameUrlPair)
        .filter(Objects::nonNull)
        .forEach(task -> processFileListTask(task, submissionAccession, context));
  }

  /**
   * Extracts fileName/filesUrl pair from a section with parent reference.
   */
  private FileListTask extractFileNameUrlPair(JsonNode section) {
    String fileName = extractFileName(section);
    String fileUrl = extractFilesUrl(section);
    return (fileName != null && fileUrl != null) ?
        new FileListTask(fileName, fileUrl, section) : null;
  }

  /**
   * Processes a single file list task asynchronously using virtual threads.
   */
  private void processFileListTask(FileListTask task, String accession, FileIndexingContext context) {
    CompletableFuture.runAsync(
        () -> processFileList(task, accession, context), executor).join();
  }

  /**
   * Core processing: fetch → partition → parallel batch indexing.
   */
  private void processFileList(FileListTask task, String accession, FileIndexingContext context) {
    log.info("Processing file list '{}' ({})", task.fileName(), task.fileUrl());

    JsonNode fileMetadata = fileListClient.fetchFileListMetadata(task.fileUrl());
    if (fileMetadata == null || !fileMetadata.has("files")) {
      log.warn("Invalid metadata for file list '{}': {}", task.fileName(), fileMetadata);
      context.getHasIndexingError().set(true);
      return;
    }

    List<JsonNode> files = streamToList(fileMetadata.get("files").elements());
    List<List<JsonNode>> batches = Lists.partition(files, FILE_BATCH_SIZE);

    context.getSectionsWithFiles().add(task.fileName());  // Track file list section

    log.debug("Processing {} files in {} batches for '{}'",
        files.size(), batches.size(), task.fileName());

    // Process batches concurrently
    List<CompletableFuture<Void>> batchFutures = batches.stream()
        .map(batch -> CompletableFuture.runAsync(
            () -> processFileBatch(task.fileName(), batch, accession, task.parentSection(), context),
            executor))
        .toList();

    CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
  }

  /**
   * Processes a batch of files by delegating to SingleFileIndexer.
   */
  private void processFileBatch(
      String fileListName,
      List<JsonNode> fileNodes,
      String accession,
      JsonNode parentSection,
      FileIndexingContext context) {

    for (JsonNode fileNode : fileNodes) {
      try {
        singleFileIndexer.indexFile(
            accession,
            context,
            parentSection,     // Original section containing fileList
            fileNode);
      } catch (Exception e) {
        log.error("Failed to index file from '{}': {}", fileListName, e.getMessage(), e);
        context.getHasIndexingError().set(true);
      }
    }
  }

  /**
   * Converts JsonNode iterator to List for partitioning.
   */
  private List<JsonNode> streamToList(Iterator<JsonNode> iterator) {
    List<JsonNode> list = new ArrayList<>();
    iterator.forEachRemaining(list::add);
    return list;
  }

  private String extractFileName(JsonNode section) {
    if (section.has(FILE_LIST_PROPERTY)) {
      JsonNode fileListNode = section.get(FILE_LIST_PROPERTY);
      if (fileListNode.has(FILE_LIST_FILE_NAME_PROPERTY)) {
        String fileName = fileListNode.get(FILE_LIST_FILE_NAME_PROPERTY).asText();
        return fileName != null && !fileName.trim().isEmpty() ? fileName : null;
      }
    }
    return null;
  }

  private String extractFilesUrl(JsonNode section) {
    if (section.has(FILE_LIST_PROPERTY)) {
      JsonNode fileListNode = section.get(FILE_LIST_PROPERTY);
      if (fileListNode.has(FILE_LIST_FILE_URL_PROPERTY)) {
        String fileUrl = fileListNode.get(FILE_LIST_FILE_URL_PROPERTY).asText();
        return fileUrl != null && !fileUrl.trim().isEmpty() ? fileUrl : null;
      }
    }
    return null;
  }

  /**
   * File list task encapsulating name, URL, and parent section reference.
   */
  private record FileListTask(String fileName, String fileUrl, JsonNode parentSection) {}
}
