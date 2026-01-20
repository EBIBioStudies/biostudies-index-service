package uk.ac.ebi.biostudies.index_service.index;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.index.IndexWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.client.FileListHttpClient;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

@ExtendWith(MockitoExtension.class)
class FileListsIndexerTest {

  @InjectMocks private FileListsIndexer fileListsIndexer;
  @Mock private FileListHttpClient fileListHttpClient;
  @Mock private SingleFileIndexer singleFileIndexer;

  private ExtendedSubmissionMetadata submissionMetadata;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    submissionMetadata = new ExtendedSubmissionMetadata();
    submissionMetadata.setAccNo("S-BIAD2077");
  }

  @Test
  void indexFileLists_nullMetadata_throwsNPE() {
    FileIndexingContext context = createTestContext();
    assertThrows(NullPointerException.class, () -> fileListsIndexer.indexFileLists(null, context));
  }

  @Test
  void indexFileLists_nullContext_throwsNPE() {
    assertThrows(
        NullPointerException.class,
        () -> fileListsIndexer.indexFileLists(submissionMetadata, null));
  }

  @Test
  void indexFileLists_singleFileList_successfullyProcessesFiles() throws IOException {
    // Given submission with complete fileList
    String json =
        """
        {
          "section": {
            "type": "Study Component",
            "accNo": "Study Component-4",
            "fileList": {
              "fileName": "filelist",
              "filesUrl": "http://test.example.com/filelist"
            }
          }
        }
        """;
    submissionMetadata.setRawSubmissionJson(objectMapper.readTree(json));

    // Mock HTTP response with 3 files
    JsonNode mockFileMetadata = createFileListResponse(3);
    when(fileListHttpClient.fetchFileListMetadata(anyString())).thenReturn(mockFileMetadata);

    FileIndexingContext context = createTestContext();

    // When
    fileListsIndexer.indexFileLists(submissionMetadata, context);

    // Then HTTP client called with correct URL
    verify(fileListHttpClient).fetchFileListMetadata("http://test.example.com/filelist");
  }

  @Test
  void findFileListSections_noFileLists_returnsEmptyList() throws IOException {
    String json =
        """
        {
          "section": {"type": "Study", "attributes": []}
        }
        """;
    JsonNode jsonNode = objectMapper.readTree(json);

    List<JsonNode> result = fileListsIndexer.findFileListSections(jsonNode);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void findFileListSections_validFileList_returnsSection() throws IOException {
    String json =
        """
        {
          "section": {
            "type": "Study Component",
            "accNo": "Study Component-4",
            "fileList": {
              "fileName": "filelist",
              "filesUrl": "/test/filelist"
            }
          }
        }
        """;
    JsonNode jsonNode = objectMapper.readTree(json);

    List<JsonNode> result = fileListsIndexer.findFileListSections(jsonNode);

    assertEquals(1, result.size());
    assertTrue(result.get(0).has("fileList"));
    assertEquals("filelist", result.get(0).get("fileList").get("fileName").textValue());
  }

  @Test
  void findFileListSections_fileListWithoutFileName_ignoresSection() throws IOException {
    String json =
        """
        {
          "section": {
            "type": "Study Component",
            "fileList": {"filesUrl": "some-url"}
          }
        }
        """;
    JsonNode jsonNode = objectMapper.readTree(json);

    List<JsonNode> result = fileListsIndexer.findFileListSections(jsonNode);

    assertTrue(result.isEmpty());
  }

  /**
   * Performance test: Verifies huge file list responses are processed without OOM or blocking.
   * Generates 10K files in-memory (~45MB JSON) to validate virtual thread partitioning.
   *
   * <p>⚠️ DISABLED by default - enable for load testing only
   */
  @Test
  @Disabled("Enable for performance testing - generates 10K files in memory")
  void indexFileLists_hugeFileListResponse_handlesLargePayloads() throws IOException {
    // Given submission with fileList pointing to huge response
    String json =
        """
        {
          "section": {
            "type": "Study Component",
            "fileList": {
              "fileName": "huge-filelist",
              "filesUrl": "http://huge.example.com/filelist"
            }
          }
        }
        """;
    submissionMetadata.setRawSubmissionJson(objectMapper.readTree(json));

    // Mock HUGE response (10K files ~45MB)
    JsonNode hugeFileList = createHugeFileListResponse(10_000);
    when(fileListHttpClient.fetchFileListMetadata(anyString())).thenReturn(hugeFileList);

    FileIndexingContext context = createTestContext();

    // When - should complete without OOM or timeout
    long startTime = System.currentTimeMillis();
    fileListsIndexer.indexFileLists(submissionMetadata, context);
    long durationMs = System.currentTimeMillis() - startTime;

    // Then - verify processing completed and HTTP client called
    verify(fileListHttpClient).fetchFileListMetadata("http://huge.example.com/filelist");
    System.out.printf("✅ Processed 10K files in %d ms%n", durationMs);
  }

  // Test helpers
  private FileIndexingContext createTestContext() {
    return FileIndexingContext.builder()
        .indexWriter(mock(IndexWriter.class))
        .fileCounter(new AtomicLong())
        .fileColumns(Collections.synchronizedSet(new HashSet<>()))
        .sectionsWithFiles(ConcurrentHashMap.newKeySet())
        .searchableFileMetadata(ConcurrentHashMap.newKeySet())
        .hasIndexingError(new AtomicBoolean(false))
        .build();
  }

  private JsonNode createFileListResponse(int numFiles) {
    ObjectNode root = objectMapper.createObjectNode();
    ArrayNode filesArray = root.putArray("files");

    for (int i = 0; i < numFiles; i++) {
      filesArray.add(createFileNode(i));
    }

    root.put("extType", "filesTable");
    return root;
  }

  private JsonNode createHugeFileListResponse(int numFiles) {
    return createFileListResponse(numFiles);
  }

  private ObjectNode createFileNode(int index) {
    ObjectNode fileNode = objectMapper.createObjectNode();
    String fileName = "file_%d.nd2".formatted(index);

    fileNode.put("fileName", fileName);
    fileNode.put("filePath", fileName);
    fileNode.put("relPath", "Files/" + fileName);
    fileNode.put("fullPath", "/nfs/.../" + fileName);
    fileNode.put("md5", "a1b2c3d4e5f6".repeat(2) + index);
    fileNode.putArray("attributes");
    fileNode.put("extType", "nfsFile");
    fileNode.put("type", "file");
    fileNode.put("size", 1024L * (100 + index % 1000)); // 100KB - 1MB

    return fileNode;
  }
}
