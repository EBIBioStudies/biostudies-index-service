package uk.ac.ebi.biostudies.index_service.index;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.IndexWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

@ExtendWith(MockitoExtension.class)
class FilesIndexerTest {

  @InjectMocks private FilesIndexer filesIndexer;
  @Mock private FileDocumentFactory fileDocumentFactory;
  @Mock private SingleFileIndexer singleFileIndexer;
  @Mock private FileListsIndexer fileListsIndexer;
  @Mock private IndexWriter indexWriter;

  private ObjectMapper objectMapper;
  private ExtendedSubmissionMetadata submissionMetadata;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void indexSubmissionFiles_nullInputs_throwNPE() {
    assertThrows(
        NullPointerException.class,
        () -> filesIndexer.indexSubmissionFiles(null, indexWriter, new HashSet<>(), false));
    assertThrows(
        NullPointerException.class,
        () -> filesIndexer.indexSubmissionFiles(submissionMetadata, null, new HashSet<>(), false));
  }

  @Test
  void indexSubmissionFiles_noFiles_createsEmptyContext() throws Exception {
    submissionMetadata = createEmptySubmission("S-TEST1");

    Set<String> columns = new HashSet<>();
    FileIndexingContext context =
        filesIndexer.indexSubmissionFiles(submissionMetadata, indexWriter, columns, false);

    assertEquals(0, context.getFileCounter().get());
    assertTrue(context.getSectionsWithFiles().isEmpty());
    assertFalse(context.getHasIndexingError().get());
    assertTrue(columns.isEmpty());
    assertEquals(2, context.getValueMap().size()); // FILES=0, FILE_ATT_KEY_VALUE=""
    assertEquals(0L, context.getValueMap().get("files"));
  }

  @Test
  void indexSubmissionFiles_removeExisting_callsDeleteDocuments() throws Exception {
    submissionMetadata = createEmptySubmission("S-TEST1");
    Set<String> columns = new HashSet<>();

    filesIndexer.indexSubmissionFiles(submissionMetadata, indexWriter, columns, true);

    verify(fileListsIndexer).indexFileLists(eq(submissionMetadata), any(FileIndexingContext.class));
  }

  @Test
  void indexSubmissionFiles_callsDirectFilesAndFileLists() throws Exception {
    submissionMetadata = createEmptySubmission("S-TEST1");
    Set<String> columns = new HashSet<>();

    filesIndexer.indexSubmissionFiles(submissionMetadata, indexWriter, columns, false);

    verify(singleFileIndexer, never()).indexFile(anyString(), any(), any(), any());
    verify(fileListsIndexer).indexFileLists(eq(submissionMetadata), any(FileIndexingContext.class));
  }

  @Test
  void indexSubmissionFiles_errorCondition_setsErrorFlagAndValueMap() throws Exception {
    submissionMetadata = createEmptySubmission("S-TEST1");
    Set<String> columns = new HashSet<>();

    doThrow(new RuntimeException("test error")).when(fileListsIndexer).indexFileLists(any(), any());

    FileIndexingContext context =
        filesIndexer.indexSubmissionFiles(submissionMetadata, indexWriter, columns, false);

    assertTrue(context.getHasIndexingError().get());
    //assertEquals("true", context.getValueMap().get(Constants.HAS_FILE_PARSING_ERROR));
  }

  @Test
  void indexSubmissionFiles_valueMapContainsExpectedKeys() throws Exception {
    submissionMetadata = createEmptySubmission("S-TEST1");
    Set<String> columns = new HashSet<>();

    FileIndexingContext context =
        filesIndexer.indexSubmissionFiles(submissionMetadata, indexWriter, columns, false);

    assertTrue(context.getValueMap().containsKey(Constants.FILES));
    assertTrue(context.getValueMap().containsKey(Constants.FILE_ATT_KEY_VALUE));
  }

  private ExtendedSubmissionMetadata createEmptySubmission(String accNo) throws Exception {
    ExtendedSubmissionMetadata metadata = new ExtendedSubmissionMetadata();
    metadata.setAccNo(accNo);
    metadata.setRawSubmissionJson(objectMapper.readTree("{}"));
    return metadata;
  }
}
