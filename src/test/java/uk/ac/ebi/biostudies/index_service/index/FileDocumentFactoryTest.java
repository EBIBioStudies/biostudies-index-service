package uk.ac.ebi.biostudies.index_service.index;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.document.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.model.ExtendedFileProperty;
import uk.ac.ebi.biostudies.index_service.model.FileDocumentField;

class FileDocumentFactoryTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private FileDocumentFactory factory;

  private IndexWriter mockWriter;
  private FileIndexingContext testContext;

  @BeforeEach
  void setUp() {
    factory = new FileDocumentFactory();
    mockWriter = mock(IndexWriter.class);
    testContext = createTestContext();
  }

  @Test
  void buildFileDocument_populatesCoreFields_whenFilePathAndNamePresent() throws Exception {
    String json = """
        {
          "fileName": "Processed_data_NGS_110_856.txt",
          "filePath": "Files/Processed_data_NGS_110_856.txt",
          "relPath": "ignored/rel/path.txt",
          "size": "7121792",
          "type": "file",
          "attributes": [
            { "name": "Description", "value": "Processed Data" },
            { "name": "Format", "value": "tab-delimited text" }
          ]
        }
        """;

    JsonNode fNode = mapper.readTree(json);
    JsonNode parent = mapper.readTree("{\"accNo\":\"S-TEST1\"}");

    Document doc = factory.buildFileDocument("S-TEST1", testContext, fNode, parent);

    // position (uses current counter value)
    assertEquals(0L, doc.getField(FileDocumentField.POSITION.getName()).numericValue().longValue());

    // size
    assertEquals(7121792L, doc.getField(FileDocumentField.SIZE.getName()).numericValue().longValue());

    // path: from filePath
    assertEquals("Files/Processed_data_NGS_110_856.txt", doc.get(FileDocumentField.PATH.getName()));

    // name: from fileName (lowercased field is returned)
    assertEquals("processed_data_ngs_110_856.txt", doc.get(FileDocumentField.NAME.getName()));

    // type and isDirectory
    assertEquals(Constants.FILE_TYPE, doc.get(FileDocumentField.TYPE.getName()));
    assertEquals("false", doc.get(ExtendedFileProperty.IS_DIRECTORY.getName()));

    // attributes: Description and Format fields exist, lowercased for indexed value
    assertEquals("processed data", doc.getField("Description").stringValue());
    assertEquals("tab-delimited text", doc.getField("Format").stringValue());

    // Context state updated correctly
    assertEquals(3, testContext.getFileColumns().size());
    assertTrue(testContext.getFileColumns().contains("Description"));
    assertTrue(testContext.getFileColumns().contains("Format"));
    assertTrue(testContext.getFileColumns().contains(Constants.SECTION));

    assertTrue(testContext.getSearchableFileMetadata().contains("Processed Data"));
    assertTrue(testContext.getSearchableFileMetadata().contains("tab-delimited text"));
    System.out.println(testContext);
  }

  @Test
  void buildFileDocument_usesRelPathAndDerivesName_whenFilePathMissing() throws Exception {
    String json = """
        {
          "relPath": "Files/only_rel_path.txt",
          "size": "10",
          "type": "file"
        }
        """;

    JsonNode fNode = mapper.readTree(json);
    JsonNode parent = mapper.readTree("{\"accNo\":\"S-TEST2\"}");

    FileIndexingContext freshContext = createTestContext();
    Document doc = factory.buildFileDocument("S-TEST2", freshContext, fNode, parent);

    // path from relPath
    assertEquals("Files/only_rel_path.txt", doc.get(FileDocumentField.PATH.getName()));

    // name derived from last segment of path (lowercased for indexed field)
    assertEquals("only_rel_path.txt", doc.get(FileDocumentField.NAME.getName()));
  }

  @Test
  void buildFileDocument_addsSectionField_whenParentIsSection() throws Exception {
    String json = """
        {
          "fileName": "file.txt",
          "filePath": "Files/file.txt",
          "size": "10",
          "type": "file"
        }
        """;

    // parent is a non-study section with accNo containing spaces and slashes
    JsonNode parent = mapper.readTree("{\"accNo\":\"SEC 1/2\",\"type\":\"Sample\"}");
    JsonNode fNode = mapper.readTree(json);

    FileIndexingContext freshContext = createTestContext();
    Document doc = factory.buildFileDocument("S-TEST3", freshContext, fNode, parent);

    assertEquals("sec12", doc.get(FileDocumentField.SECTION.getName()));

    // Context should contain SECTION constant
    assertTrue(freshContext.getFileColumns().contains(Constants.SECTION));
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
}
