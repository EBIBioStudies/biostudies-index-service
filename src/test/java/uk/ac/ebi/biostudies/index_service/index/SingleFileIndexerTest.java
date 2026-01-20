package uk.ac.ebi.biostudies.index_service.index;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.model.FileDocumentField;

@ExtendWith(MockitoExtension.class)
class SingleFileIndexerTest {

  @InjectMocks private SingleFileIndexer singleFileIndexer;
  @Mock private FileDocumentFactory fileDocumentFactory;
  @Mock private IndexWriter indexWriter;

  private FileIndexingContext context;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    context = createTestContext();
  }

  @Test
  void indexFile_successfulIndexing_updatesAllContextState() throws IOException {
    String accession = "S-TEST123";
    JsonNode parent = createParentNode("Section-1");
    JsonNode fileNode = createTestFileNode("test.nd2", 1024L);

    Document mockDoc = createMockDocument("test.nd2", "Section-1");
    when(fileDocumentFactory.buildFileDocument(
            eq(accession), eq(context), eq(fileNode), eq(parent)))
        .thenReturn(mockDoc);

    long initialCounter = context.getFileCounter().get();

    assertDoesNotThrow(() -> singleFileIndexer.indexFile(accession, context, parent, fileNode));

    verify(indexWriter)
        .updateDocument(
            argThat(
                term ->
                    FileDocumentField.ID.getName().equals(term.field())
                        && "S-TEST123-0".equals(term.text())),
            eq(mockDoc));

    assertEquals(initialCounter + 1, context.getFileCounter().get());
    assertTrue(context.getSectionsWithFiles().contains("Section-1"));
    assertFalse(context.getHasIndexingError().get());
  }

  @Test
  void indexFile_sectionNotPresent_doesNotUpdateSections() throws IOException {
    String accession = "S-TEST123";
    JsonNode parent = createParentNode(null);
    JsonNode fileNode = createTestFileNode("test.nd2", 1024L);

    Document mockDoc = createMockDocument("test.nd2", null);
    when(fileDocumentFactory.buildFileDocument(
            anyString(), eq(context), any(JsonNode.class), any(JsonNode.class)))
        .thenReturn(mockDoc);

    assertDoesNotThrow(() -> singleFileIndexer.indexFile(accession, context, parent, fileNode));

    assertTrue(context.getSectionsWithFiles().isEmpty());
    verify(indexWriter).updateDocument(any(Term.class), eq(mockDoc));
  }

  @Test
  void indexFile_factoryFailure_setsErrorFlag() {
    String accession = "S-TEST123";
    JsonNode parent = createParentNode("Section-1");
    JsonNode fileNode = createTestFileNode("test.nd2", 1024L);

    when(fileDocumentFactory.buildFileDocument(
            anyString(), eq(context), any(JsonNode.class), any(JsonNode.class)))
        .thenThrow(new RuntimeException("Factory blew up"));

    assertThrows(
        IOException.class, () -> singleFileIndexer.indexFile(accession, context, parent, fileNode));

    assertTrue(context.getHasIndexingError().get());
    assertEquals(1, context.getFileCounter().get());
    verifyNoInteractions(indexWriter);
  }

  @Test
  void indexFile_luceneUpdateFailure_propagatesIOException() throws IOException {
    String accession = "S-TEST123";
    JsonNode parent = createParentNode("Section-1");
    JsonNode fileNode = createTestFileNode("test.nd2", 1024L);

    Document mockDoc = createMockDocument("test.nd2", "Section-1");
    when(fileDocumentFactory.buildFileDocument(
            anyString(), eq(context), any(JsonNode.class), any(JsonNode.class)))
        .thenReturn(mockDoc);

    doThrow(new IOException("Disk full"))
        .when(indexWriter)
        .updateDocument(any(Term.class), any(Document.class));

    assertThrows(
        IOException.class, () -> singleFileIndexer.indexFile(accession, context, parent, fileNode));

    assertTrue(context.getHasIndexingError().get());
    assertEquals(1, context.getFileCounter().get());
  }

  @Test
  void indexFile_multipleCalls_incrementPositionSequentially() throws IOException {
    String accession = "S-TEST123";
    JsonNode parent = createParentNode("Section-1");
    JsonNode fileNode = createTestFileNode("test.nd2", 1024L);

    Document mockDoc = createMockDocument("test.nd2", "Section-1");
    when(fileDocumentFactory.buildFileDocument(
            anyString(), eq(context), any(JsonNode.class), any(JsonNode.class)))
        .thenReturn(mockDoc);

    assertDoesNotThrow(() -> singleFileIndexer.indexFile(accession, context, parent, fileNode));
    assertDoesNotThrow(() -> singleFileIndexer.indexFile(accession, context, parent, fileNode));

    verify(indexWriter, times(2)).updateDocument(any(Term.class), eq(mockDoc));
    assertEquals(2, context.getFileCounter().get());
    assertEquals(1, context.getSectionsWithFiles().size());
  }

  @Test
  void indexFile_concurrentCalls_maintainsUniquePositions()
      throws InterruptedException, IOException {
    String accession = "S-TEST123";
    JsonNode parent = createParentNode("Section-1");
    JsonNode fileNode = createTestFileNode("concurrent.nd2", 1024L);

    Document mockDoc = createMockDocument("concurrent.nd2", "Section-1");
    when(fileDocumentFactory.buildFileDocument(
            anyString(), eq(context), any(JsonNode.class), any(JsonNode.class)))
        .thenReturn(mockDoc);

    Thread[] threads = new Thread[5];
    for (int i = 0; i < 5; i++) {
      final int id = i;
      threads[i] =
          new Thread(
              () -> {
                try {
                  singleFileIndexer.indexFile(accession, context, parent, fileNode);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
      threads[i].start();
    }

    for (Thread t : threads) {
      t.join();
    }

    assertEquals(5, context.getFileCounter().get());
    verify(indexWriter, times(5)).updateDocument(any(Term.class), eq(mockDoc));
    assertEquals(1, context.getSectionsWithFiles().size());
    assertFalse(context.getHasIndexingError().get());
  }

  private FileIndexingContext createTestContext() {
    return FileIndexingContext.builder()
        .indexWriter(indexWriter)
        .fileCounter(new AtomicLong())
        .fileColumns(Collections.synchronizedSet(new HashSet<>()))
        .sectionsWithFiles(ConcurrentHashMap.newKeySet())
        .searchableFileMetadata(ConcurrentHashMap.newKeySet())
        .hasIndexingError(new AtomicBoolean(false))
        .build();
  }

  private JsonNode createParentNode(String accNo) {
    ObjectNode node = objectMapper.createObjectNode();
    if (accNo != null) {
      node.put("accNo", accNo);
      node.put("type", "Sample");
    }
    return node;
  }

  private JsonNode createTestFileNode(String filename, long size) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("fileName", filename);
    node.put("filePath", "Files/" + filename);
    node.put("size", size);
    node.put("type", "file");
    return node;
  }

  private Document createMockDocument(String filename, String sectionName) {
    Document doc = new Document();

    doc.add(new NumericDocValuesField(FileDocumentField.POSITION.getName(), 0L));
    doc.add(new StoredField(FileDocumentField.POSITION.getName(), 0L));

    doc.add(new StringField(FileDocumentField.NAME.getName(), filename.toLowerCase(), Store.NO));
    doc.add(new StoredField(FileDocumentField.NAME.getName(), filename));

    if (sectionName != null) {
      doc.add(
          new StringField(
              FileDocumentField.SECTION.getName(), sectionName.toLowerCase(), Store.NO));
      doc.add(new StoredField(FileDocumentField.SECTION.getName(), sectionName));
    }

    return doc;
  }
}
