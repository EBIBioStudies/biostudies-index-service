package uk.ac.ebi.biostudies.index_service.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;
import uk.ac.ebi.biostudies.index_service.util.DocumentToMap;
import uk.ac.ebi.biostudies.index_service.util.JsonFileLoader;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class SubmissionIndexerIntegrationTest {

  private static final String INDEXING_FILES_DIR = "data/indexing";
  private static final int MAX_NUM_FILES = 100;
  private static boolean commit = true;

  @TempDir
  static java.nio.file.Path tempDir;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("index.base-dir", () -> tempDir.toAbsolutePath().toString());
  }

  @Autowired private SubmissionIndexer submissionIndexer;
  @Autowired private IndexManager indexManager;

  @BeforeAll
  static void setUp() {}

  @AfterAll
  static void tearDown() {
    // @TempDir handles cleanup automatically
  }

  private ExtendedSubmissionMetadata createExtendedSubmissionMetadata(
      String accNo, String jsonFilePath) throws IOException {
    JsonNode submissionJson = JsonFileLoader.loadJsonFromResources(jsonFilePath);
    ExtendedSubmissionMetadata metadata = new ExtendedSubmissionMetadata();
    metadata.setAccNo(accNo);
    metadata.setRawSubmissionJson(submissionJson);
    return metadata;
  }

  @Test
  void shouldIndexArrayExpressStudy() throws IOException {
    String accNo = "E-MTAB-16149";
    String testDataDir = "ArrayExpress_Study_E-MTAB-16149";
    String inputFile = testDataDir + "/E-MTAB-16149.json";
    String expectedSubmissionMetadataDir =
        testDataDir + "/E-MTAB-16149_expected_submission_output.json";
    String expectedFilesDir = testDataDir + "/E-MTAB-16149_expected_files_output.json";
    runIndexingTestScenario(accNo, inputFile, expectedSubmissionMetadataDir, expectedFilesDir);
  }

  private Document findSubmissionByAccession(String accNo) throws IOException {
    try (DirectoryReader submissionReader =
        DirectoryReader.open(indexManager.getSubmissionIndexWriter().getDirectory())) {

      IndexSearcher searcher = new IndexSearcher(submissionReader);
      TopDocs hits = searcher.search(new TermQuery(new Term(FieldName.ID.getName(), accNo)), 10);
      List<Document> docs = new ArrayList<>();
      StoredFields storedFields = searcher.storedFields();
      for (ScoreDoc scoreDoc : hits.scoreDocs) {
        Document doc = storedFields.document(scoreDoc.doc);
        docs.add(doc);
      }
      if (docs.size() != 1) {
        throw new IllegalStateException("Expected only 1 document");
      }
      return docs.getFirst();
    }
  }

  void runIndexingTestScenario(
      String accNo, String inputPath, String expectedSubmissionMetadataDir, String expectedFilesDir)
      throws IOException {
    ExtendedSubmissionMetadata submissionMetadata =
        createExtendedSubmissionMetadata(
            accNo, Paths.get(INDEXING_FILES_DIR, inputPath).toString());

    submissionIndexer.indexOne(submissionMetadata, true, commit);

    Document submissionDoc = findSubmissionByAccession(accNo);

    Map<String, Object> result = DocumentToMap.convert(submissionDoc);

    Map<String, Object> expected =
        JsonFileLoader.loadExpectedMapFromResources(
            Paths.get(INDEXING_FILES_DIR, expectedSubmissionMetadataDir).toString());
    assertExpectedIndexedValues(result, expected);
    assertPageTabIndexDocDeleted(accNo);

    List<Document> fileDocuments = findByOwnerOrderByPosition(accNo);
    List<Map<String, Object>> expectedFiles =
        JsonFileLoader.loadExpectedListOfMapsFromResources(
            Paths.get(INDEXING_FILES_DIR, expectedFilesDir).toString());
    assertExpectedFilesIndexed(fileDocuments, expectedFiles);
  }

  private void assertExpectedFilesIndexed(
      List<Document> fileDocuments, List<Map<String, Object>> expectedFiles) {
    for (int i = 0; i < fileDocuments.size(); i++) {
      Map<String, Object> docAsMap = DocumentToMap.convert(fileDocuments.get(i));
      Map<String, Object> expected = expectedFiles.get(i);
      assertExpectedIndexedValues(docAsMap, expected);
    }
  }

  private void assertPageTabIndexDocDeleted(String accNo) throws IOException {
    // PAGE_TAB index - verify delete + document exists
    try (DirectoryReader pageTabReader =
        DirectoryReader.open(indexManager.getPageTabIndexWriter().getDirectory())) {
      IndexSearcher searcher = new IndexSearcher(pageTabReader);
      TopDocs hits =
          searcher.search(new TermQuery(new Term(FieldName.ACCESSION.getName(), accNo)), 10);
      assertEquals(0, hits.totalHits.value(), "There should not be results in pagetab");
    }
  }

  private String normalizeReleaseDate(Object value) {
    if (value == null) return null;

    String str = value.toString().trim();
    if (str.isEmpty()) return str;

    // Allow timestamps like `2026-01-01T00:00:00Z` to be compared against `yyyy-MM-dd`
    if (str.length() >= 10
        && Character.isDigit(str.charAt(0))
        && Character.isDigit(str.charAt(1))
        && Character.isDigit(str.charAt(2))
        && Character.isDigit(str.charAt(3))
        && str.charAt(4) == '-'
        && str.charAt(7) == '-') {
      return str.substring(0, 10);
    }

    return str;
  }

  private boolean releaseDateMatches(Object expectedValue, Object actualValue) {
    String expectedStr = normalizeReleaseDate(expectedValue);
    String actualStr = normalizeReleaseDate(actualValue);

    if (expectedStr == null || actualStr == null) {
      return expectedStr == null && actualStr == null;
    }

    if (expectedStr.equals(actualStr)) {
      return true;
    }

    // release_date can be derived from epoch millis and be timezone sensitive at day boundaries.
    // Allow +/- 1 day drift when both values are ISO dates.
    try {
      LocalDate expectedDate = LocalDate.parse(expectedStr);
      LocalDate actualDate = LocalDate.parse(actualStr);
      long days = Math.abs(ChronoUnit.DAYS.between(expectedDate, actualDate));
      return days <= 1;
    } catch (DateTimeParseException ignored) {
      return false;
    }
  }

  private void assertExpectedIndexedValues(Map<String, Object> actual, Map<String, Object> expected) {

    if (!actual.keySet().equals(expected.keySet())) {
      log.error("Key mismatch: {}", keySetDiff(actual.keySet(), expected.keySet()));
      fail(keySetDiff(actual.keySet(), expected.keySet()));
    }

    for (Entry<String, Object> entry : expected.entrySet()) {
      String key = entry.getKey();
      if (!actual.containsKey(key)) {
        fail("Key " + key + " not found in " + actual);
      }
      Object actualValue = actual.get(key);
      Object expectedValue = expected.get(key);

      if ("release_date".equals(key)) {
        assertTrue(
            releaseDateMatches(expectedValue, actualValue),
            "Value of {release_date} not as expected (expected="
                + normalizeReleaseDate(expectedValue)
                + ", actual="
                + normalizeReleaseDate(actualValue)
                + ")");
        continue;
      }

      assertEquals(expectedValue, actualValue, "Value of {" + key + "} not as expected");
    }
  }

  private String keySetDiff(Set<String> actual, Set<String> expected) {
    Set<String> missing = new HashSet<>(expected);
    missing.removeAll(actual);

    Set<String> unexpected = new HashSet<>(actual);
    unexpected.removeAll(expected);

    return String.format(
        "Missing: %s, Unexpected: %s (%d vs %d keys)",
        missing, unexpected, actual.size(), expected.size());
  }

  private List<Document> findByOwnerOrderByPosition(String accNo) throws IOException {
    try (DirectoryReader reader =
        DirectoryReader.open(indexManager.getFilesIndexWriter().getDirectory())) {
      IndexSearcher searcher = new IndexSearcher(reader);

      // file_owner == accNo
      Query ownerQuery = new TermQuery(new Term("file_owner", accNo));

      // sort by file_position (numeric long field)
      Sort sort = new Sort(new SortField("file_position", SortField.Type.LONG, false));

      TopDocs topDocs = searcher.search(ownerQuery, MAX_NUM_FILES, sort);

      List<Document> docs = new ArrayList<>();
      StoredFields storedFields = searcher.storedFields();
      for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
        Document doc = storedFields.document(scoreDoc.doc);
        docs.add(doc);
      }
      return docs;
    }
  }
}
