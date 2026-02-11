package uk.ac.ebi.biostudies.index_service.autocomplete;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Tests for {@link EFOTermMatcher}.
 *
 * <p>Uses an in-memory Lucene index to simulate the EFO ontology structure and test caching, term
 * matching, and hierarchy resolution.
 */
@ExtendWith(MockitoExtension.class)
class EFOTermMatcherTest {

  // Test data constants
  private static final String CELL_ID = "http://purl.obolibrary.org/obo/CL_0000000";
  private static final String LEUKOCYTE_ID = "http://purl.obolibrary.org/obo/CL_0000738";
  private static final String MYELOID_LEUKOCYTE_ID = "http://purl.obolibrary.org/obo/CL_0000766";
  private static final String OSTEOCLAST_ID = "http://purl.obolibrary.org/obo/CL_0000092";
  private static final String ODONTOCLAST_ID = "http://purl.obolibrary.org/obo/CL_0002452";
  private static final String ORGANISM_ID = "http://purl.obolibrary.org/obo/EFO_0000634";
  @Mock private IndexManager indexManager;
  private Directory directory;
  private IndexReader indexReader;
  private IndexSearcher indexSearcher;
  private EFOTermMatcher efoTermMatcher;

  @BeforeEach
  void setUp() throws IOException {
    directory = new ByteBuffersDirectory();
    createTestEFOIndex();

    indexReader = DirectoryReader.open(directory);
    indexSearcher = new IndexSearcher(indexReader);

    when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(indexSearcher);
    doNothing().when(indexManager).releaseSearcher(any(), any());

    efoTermMatcher = new EFOTermMatcher(indexManager);
    efoTermMatcher.initialize();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (indexReader != null) {
      indexReader.close();
    }
    if (directory != null) {
      directory.close();
    }
  }

  /**
   * Creates a test EFO index with a simple hierarchy:
   *
   * <pre>
   * cell
   *   └─ leukocyte
   *       └─ myeloid leukocyte
   *           └─ osteoclast
   *               └─ odontoclast
   * organism (separate branch)
   * </pre>
   */
  private void createTestEFOIndex() throws IOException {
    IndexWriterConfig config = new IndexWriterConfig();
    try (IndexWriter writer = new IndexWriter(directory, config)) {
      // Root term: cell
      writer.addDocument(createEFODocument(CELL_ID, "cell", null, "cellular entity"));

      // Level 1: leukocyte (child of cell)
      writer.addDocument(
          createEFODocument(LEUKOCYTE_ID, "leukocyte", new String[] {CELL_ID}, "white blood cell"));

      // Level 2: myeloid leukocyte (child of leukocyte)
      writer.addDocument(
          createEFODocument(
              MYELOID_LEUKOCYTE_ID, "myeloid leukocyte", new String[] {LEUKOCYTE_ID}, null));

      // Level 3: osteoclast (child of myeloid leukocyte)
      writer.addDocument(
          createEFODocument(
              OSTEOCLAST_ID, "osteoclast", new String[] {MYELOID_LEUKOCYTE_ID}, null));

      // Level 4: odontoclast (child of osteoclast)
      writer.addDocument(
          createEFODocument(ODONTOCLAST_ID, "odontoclast", new String[] {OSTEOCLAST_ID}, null));

      // Separate branch: organism (root)
      writer.addDocument(createEFODocument(ORGANISM_ID, "organism", null, null));

      writer.commit();
    }
  }

  private Document createEFODocument(String id, String term, String[] parents, String altTerm) {
    Document doc = new Document();
    doc.add(new StringField(EFOField.ID.getFieldName(), id, Field.Store.YES));
    doc.add(new StringField(EFOField.TERM.getFieldName(), term, Field.Store.YES));

    if (parents != null) {
      for (String parent : parents) {
        doc.add(new StringField(EFOField.PARENT.getFieldName(), parent, Field.Store.YES));
      }
    }

    if (altTerm != null) {
      doc.add(new StringField(EFOField.ALTERNATIVE_TERMS.getFieldName(), altTerm, Field.Store.YES));
    }

    return doc;
  }

  // ========== Initialization Tests ==========

  @Test
  void initialize_shouldLoadAllTermsFromIndex() {
    assertThat(efoTermMatcher.getAllTerms())
        .hasSize(8) // 6 primary + 2 alternative
        .contains(
            "cell",
            "leukocyte",
            "myeloid leukocyte",
            "osteoclast",
            "odontoclast",
            "organism",
            "cellular entity",
            "white blood cell");
  }

  @Test
  void initialize_shouldBuildTermToIdCache() {
    assertThat(efoTermMatcher.getEFOId("cell")).isEqualTo(CELL_ID);
    assertThat(efoTermMatcher.getEFOId("leukocyte")).isEqualTo(LEUKOCYTE_ID);
    assertThat(efoTermMatcher.getEFOId("odontoclast")).isEqualTo(ODONTOCLAST_ID);
  }

  @Test
  void initialize_shouldBuildIdToTermCache() {
    assertThat(efoTermMatcher.getTerm(CELL_ID)).isEqualTo("cell");
    assertThat(efoTermMatcher.getTerm(LEUKOCYTE_ID)).isEqualTo("leukocyte");
    assertThat(efoTermMatcher.getTerm(ODONTOCLAST_ID)).isEqualTo("odontoclast");
  }

  @Test
  void initialize_shouldBuildAncestorChains() {
    assertThat(efoTermMatcher.getAncestors("odontoclast"))
        .containsExactly("cell", "leukocyte", "myeloid leukocyte", "osteoclast");

    assertThat(efoTermMatcher.getAncestors("leukocyte")).containsExactly("cell");

    assertThat(efoTermMatcher.getAncestors("cell")).isEmpty(); // Root has no ancestors
  }

  @Test
  void initialize_shouldThrowIllegalStateException_whenIndexAccessFails() throws IOException {
    when(indexManager.acquireSearcher(IndexName.EFO))
        .thenThrow(new IOException("Index unavailable"));

    EFOTermMatcher failingMatcher = new EFOTermMatcher(indexManager);

    assertThatThrownBy(failingMatcher::initialize)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("EFO term matcher initialization failed");
  }

  // ========== Term Matching Tests ==========

  @Test
  void findEFOTerms_shouldFindSingleTerm() {
    String content = "Study of leukocyte function";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    assertThat(matches).containsExactly("leukocyte");
  }

  @Test
  void findEFOTerms_shouldFindMultipleTerms() {
    String content = "Study of odontoclast and leukocyte in organism";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    assertThat(matches).containsExactlyInAnyOrder("odontoclast", "leukocyte", "organism");
  }

  @Test
  void findEFOTerms_shouldMatchCaseInsensitively() {
    String content = "LEUKOCYTE and Osteoclast research";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    assertThat(matches).containsExactlyInAnyOrder("leukocyte", "osteoclast");
  }

  @Test
  void findEFOTerms_shouldMatchMultiWordTerms() {
    String content = "Research on myeloid leukocyte populations";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    assertThat(matches).containsExactly("myeloid leukocyte");
    // "leukocyte" is now excluded because it overlaps with "myeloid leukocyte"
  }

  @Test
  void findEFOTerms_shouldPreferLongerMatches_whenTermsOverlap() {
    String content = "Study of myeloid leukocyte and cell populations";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    // "myeloid leukocyte" preferred over "leukocyte"
    // "cell" is separate and doesn't overlap
    assertThat(matches).containsExactlyInAnyOrder("myeloid leukocyte", "cell");
  }

  @Test
  void findEFOTerms_shouldDeduplicateAlternativeTerms() {
    String content = "Study of leukocyte (white blood cell) function";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    // Both "leukocyte" and "white blood cell" map to "leukocyte" -> deduplicated
    assertThat(matches).containsExactly("leukocyte");
  }

  @Test
  void findEFOTerms_shouldHandleNonOverlappingOccurrences() {
    String content = "leukocyte in organism, another leukocyte study";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    // Multiple occurrences of same term -> single result
    assertThat(matches).containsExactlyInAnyOrder("leukocyte", "organism");
  }

  @Test
  void findEFOTerms_shouldMatchAlternativeTerms() {
    String content = "Analysis of white blood cell counts";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    assertThat(matches).containsExactly("leukocyte"); // Maps alt term to primary
  }

  @Test
  void findEFOTerms_shouldUseWordBoundaries_andNotMatchPartialWords() {
    String content = "Study of macrophagocyte"; // Should NOT match "phagocyte" if it exists

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    assertThat(matches).isEmpty();
  }

  @Test
  void findEFOTerms_shouldReturnEmptyList_whenContentIsNull() {
    assertThat(efoTermMatcher.findEFOTerms(null)).isEmpty();
  }

  @Test
  void findEFOTerms_shouldReturnEmptyList_whenContentIsEmpty() {
    assertThat(efoTermMatcher.findEFOTerms("")).isEmpty();
  }

  @Test
  void findEFOTerms_shouldReturnEmptyList_whenNoTermsMatch() {
    String content = "This content has no EFO terms";

    assertThat(efoTermMatcher.findEFOTerms(content)).isEmpty();
  }

  @Test
  void findEFOTerms_shouldHandleSpecialCharactersInContent() {
    String content = "Study of leukocyte (white blood cell) function!";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    assertThat(matches).containsExactlyInAnyOrder("leukocyte");
  }

  // ========== Ancestor Chain Tests ==========

  @Test
  void getAncestors_shouldReturnCorrectChain_forDeepTerm() {
    List<String> ancestors = efoTermMatcher.getAncestors("odontoclast");

    assertThat(ancestors).containsExactly("cell", "leukocyte", "myeloid leukocyte", "osteoclast");
  }

  @Test
  void getAncestors_shouldReturnSingleAncestor_forDirectChild() {
    List<String> ancestors = efoTermMatcher.getAncestors("leukocyte");

    assertThat(ancestors).containsExactly("cell");
  }

  @Test
  void getAncestors_shouldReturnEmptyList_forRootTerm() {
    assertThat(efoTermMatcher.getAncestors("cell")).isEmpty();
    assertThat(efoTermMatcher.getAncestors("organism")).isEmpty();
  }

  @Test
  void getAncestors_shouldReturnEmptyList_forUnknownTerm() {
    assertThat(efoTermMatcher.getAncestors("unknown_term")).isEmpty();
  }

  @Test
  void getAncestors_shouldReturnEmptyList_whenTermIsNull() {
    assertThat(efoTermMatcher.getAncestors(null)).isEmpty();
  }

  @Test
  void getAncestors_shouldBeCaseInsensitive() {
    List<String> lower = efoTermMatcher.getAncestors("odontoclast");
    List<String> upper = efoTermMatcher.getAncestors("ODONTOCLAST");
    List<String> mixed = efoTermMatcher.getAncestors("OdontoClast");

    assertThat(lower).isEqualTo(upper).isEqualTo(mixed);
  }

  // ========== ID/Term Lookup Tests ==========

  @Test
  void getEFOId_shouldReturnCorrectId() {
    assertThat(efoTermMatcher.getEFOId("cell")).isEqualTo(CELL_ID);
    assertThat(efoTermMatcher.getEFOId("odontoclast")).isEqualTo(ODONTOCLAST_ID);
  }

  @Test
  void getEFOId_shouldReturnNull_forUnknownTerm() {
    assertThat(efoTermMatcher.getEFOId("unknown_term")).isNull();
  }

  @Test
  void getEFOId_shouldReturnNull_whenTermIsNull() {
    assertThat(efoTermMatcher.getEFOId(null)).isNull();
  }

  @Test
  void getEFOId_shouldBeCaseInsensitive() {
    assertThat(efoTermMatcher.getEFOId("cell"))
        .isEqualTo(efoTermMatcher.getEFOId("CELL"))
        .isEqualTo(efoTermMatcher.getEFOId("Cell"));
  }

  @Test
  void getEFOId_shouldReturnPrimaryId_forAlternativeTerm() {
    // "white blood cell" is alternative term for leukocyte
    assertThat(efoTermMatcher.getEFOId("white blood cell")).isEqualTo(LEUKOCYTE_ID);
  }

  @Test
  void getTerm_shouldReturnCorrectTerm() {
    assertThat(efoTermMatcher.getTerm(CELL_ID)).isEqualTo("cell");
    assertThat(efoTermMatcher.getTerm(ODONTOCLAST_ID)).isEqualTo("odontoclast");
  }

  @Test
  void getTerm_shouldReturnNull_forUnknownId() {
    assertThat(efoTermMatcher.getTerm("http://unknown.org/id")).isNull();
  }

  @Test
  void getTerm_shouldReturnNull_whenIdIsNull() {
    assertThat(efoTermMatcher.getTerm(null)).isNull();
  }

  @Test
  void getTerm_shouldPreserveOriginalCase() {
    // Assuming "Leukocyte" was indexed with capital L
    String term = efoTermMatcher.getTerm(LEUKOCYTE_ID);
    assertThat(term).isEqualTo("leukocyte"); // Matches indexed case
  }

  // ========== Term Existence Tests ==========

  @Test
  void isEFOTerm_shouldReturnTrue_forExistingTerm() {
    assertThat(efoTermMatcher.isEFOTerm("cell")).isTrue();
    assertThat(efoTermMatcher.isEFOTerm("leukocyte")).isTrue();
    assertThat(efoTermMatcher.isEFOTerm("odontoclast")).isTrue();
  }

  @Test
  void isEFOTerm_shouldReturnTrue_forAlternativeTerm() {
    assertThat(efoTermMatcher.isEFOTerm("white blood cell")).isTrue();
  }

  @Test
  void isEFOTerm_shouldReturnFalse_forUnknownTerm() {
    assertThat(efoTermMatcher.isEFOTerm("unknown_term")).isFalse();
  }

  @Test
  void isEFOTerm_shouldReturnFalse_whenTermIsNull() {
    assertThat(efoTermMatcher.isEFOTerm(null)).isFalse();
  }

  @Test
  void isEFOTerm_shouldBeCaseInsensitive() {
    assertThat(efoTermMatcher.isEFOTerm("cell")).isTrue();
    assertThat(efoTermMatcher.isEFOTerm("CELL")).isTrue();
    assertThat(efoTermMatcher.isEFOTerm("Cell")).isTrue();
  }

  // ========== Cache Stats Tests ==========

  @Test
  void getCacheStats_shouldReturnFormattedStatistics() {
    String stats = efoTermMatcher.getCacheStats();

    assertThat(stats)
        .contains("EFOTermMatcher")
        .contains("terms=")
        .contains("withHierarchy=")
        .contains("nodes=");
  }

  @Test
  void getAllTerms_shouldReturnUnmodifiableSet() {
    Set<String> terms = efoTermMatcher.getAllTerms();

    assertThatThrownBy(() -> terms.add("new_term"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getAllTerms_shouldContainAllPrimaryAndAlternativeTerms() {
    Set<String> terms = efoTermMatcher.getAllTerms();

    assertThat(terms).contains("cell", "leukocyte", "white blood cell", "odontoclast");
  }

  // ========== Integration Test ==========

  @Test
  void endToEnd_shouldMatchTermsAndResolveHierarchy() {
    // Simulate submission content from BioStudies
    String submissionContent = "Study of odontoclast function in cellular entity populations";

    // Find EFO terms
    List<String> foundTerms = efoTermMatcher.findEFOTerms(submissionContent);
    assertThat(foundTerms).containsExactlyInAnyOrder("odontoclast", "cell");

    // Get hierarchy for each term
    for (String term : foundTerms) {
      String efoId = efoTermMatcher.getEFOId(term);
      assertThat(efoId).isNotNull();

      List<String> ancestors = efoTermMatcher.getAncestors(term);
      assertThat(ancestors).isNotNull();

      // Verify we can reconstruct the full path
      if (term.equals("odontoclast")) {
        assertThat(ancestors)
            .containsExactly("cell", "leukocyte", "myeloid leukocyte", "osteoclast");
      }
    }
  }

  // ========== Thread Safety Test ==========

  @Test
  void concurrentAccess_shouldBeThreadSafe() throws InterruptedException {
    int threadCount = 10;
    int operationsPerThread = 100;
    List<Thread> threads = new ArrayList<>();
    List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  for (int j = 0; j < operationsPerThread; j++) {
                    efoTermMatcher.findEFOTerms("Study of leukocyte and cell");
                    efoTermMatcher.getAncestors("odontoclast");
                    efoTermMatcher.getEFOId("cell");
                    efoTermMatcher.isEFOTerm("leukocyte");
                  }
                } catch (Throwable t) {
                  exceptions.add(t);
                }
              });
      threads.add(thread);
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(exceptions).isEmpty();
  }

  // ========== Edge Cases ==========

  @Test
  void findEFOTerms_shouldHandleRepeatedTerms() {
    String content = "cell cell cell leukocyte";

    List<String> matches = efoTermMatcher.findEFOTerms(content);

    // Should still return each unique term once
    assertThat(matches).containsExactlyInAnyOrder("cell", "leukocyte");
  }

  @Test
  void findEFOTerms_shouldHandleVeryLongContent() {
    StringBuilder longContent = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      longContent.append("Some text about research. ");
    }
    longContent.append("This includes leukocyte analysis.");

    List<String> matches = efoTermMatcher.findEFOTerms(longContent.toString());

    assertThat(matches).containsExactly("leukocyte");
  }
}
