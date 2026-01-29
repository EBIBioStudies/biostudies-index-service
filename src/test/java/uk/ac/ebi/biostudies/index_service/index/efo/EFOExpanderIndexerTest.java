package uk.ac.ebi.biostudies.index_service.index.efo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.config.EFOConfig;

@ExtendWith(MockitoExtension.class)
class EFOExpanderIndexerTest {

  @TempDir Path tempDir;

  @Mock private EFOConfig efoConfig;

  private EFOExpanderIndexer indexer;
  private Directory directory;
  private IndexWriter writer;

  @BeforeEach
  void setUp() throws IOException {
    indexer = new EFOExpanderIndexer(efoConfig);

    directory = FSDirectory.open(tempDir);
    IndexWriterConfig config = new IndexWriterConfig();
    writer = new IndexWriter(directory, config);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (writer != null && writer.isOpen()) {
      writer.close();
    }
    if (directory != null) {
      directory.close();
    }
  }

  @Test
  void testBuildIndex_WithValidResolver_CreatesDocuments() throws IOException {
    stubDefaultStopWords();

    // Given: Simple ontology with disease as root
    EFONode disease = createNode(EFOTermResolver.ROOT_ID, "disease");
    disease.getAlternativeTerms().add("illness");

    EFONode cancer = createNode("EFO_0000311", "cancer");
    cancer.getAlternativeTerms().add("neoplasm");
    disease.addChild(cancer);

    EFOModel model = EFOModel.builder().addNode(disease).addNode(cancer).build();

    EFOTermResolver resolver = new EFOTermResolver(model, "test-v1");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertTrue(reader.numDocs() > 0, "Should create expansion documents");
    }
  }

  @Test
  void testBuildIndex_WithNullResolver_SkipsIndexing() throws IOException {
    // No stubbing here on purpose (avoids UnnecessaryStubbingException)
    indexer.buildIndex(null, writer);
    writer.commit();
    writer.close();

    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertEquals(0, reader.numDocs(), "Should not create documents for null resolver");
    }
  }

  @Test
  void testExpansionDocument_ContainsPrimaryTerm() throws IOException {
    stubDefaultStopWords();

    // Given: Node with term only
    EFONode root = createRoot();
    EFONode node = createNode("EFO_0000311", "cancer");
    node.getAlternativeTerms().add("neoplasm");
    root.addChild(node);

    EFOModel model = EFOModel.builder().addNode(root).addNode(node).build();
    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then
    try (IndexReader reader = DirectoryReader.open(directory)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      TopDocs results = searcher.search(new TermQuery(new Term(EFOIndexFields.TERM, "cancer")), 10);

      assertTrue(results.totalHits.value() > 0, "Primary term should be searchable");
    }
  }

  @Test
  void testExpansionDocument_ContainsAlternativeTerms() throws IOException {
    stubDefaultStopWords();

    // Given: Node with synonyms
    EFONode root = createRoot();
    EFONode cancer = createNode("EFO_0000311", "cancer");
    cancer.getAlternativeTerms().add("neoplasm");
    cancer.getAlternativeTerms().add("tumor");
    root.addChild(cancer);

    EFOModel model = EFOModel.builder().addNode(root).addNode(cancer).build();
    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then
    try (IndexReader reader = DirectoryReader.open(directory)) {
      StoredFields storedFields = reader.storedFields();
      Document doc = storedFields.document(0);
      String[] terms = doc.getValues(EFOIndexFields.TERM);
      String[] expansions = doc.getValues(EFOIndexFields.EFO);

      assertTrue(List.of(terms).contains("cancer"), "Should contain primary term");
      assertTrue(List.of(terms).contains("neoplasm"), "Should contain synonym as searchable");
      assertTrue(List.of(expansions).contains("neoplasm"), "Should contain synonym as expansion");
    }
  }

  @Test
  void testExpansionDocument_ContainsChildTerms() throws IOException {
    stubDefaultStopWords();

    // Given: Parent with children
    EFONode disease = createNode(EFOTermResolver.ROOT_ID, "disease");
    disease.getAlternativeTerms().add("illness");

    EFONode cancer = createNode("EFO_0000311", "cancer");
    EFONode lungCancer = createNode("EFO_0001234", "lung cancer");

    disease.addChild(cancer);
    cancer.addChild(lungCancer);

    EFOModel.Builder builder = EFOModel.builder();
    builder.addNode(disease);
    builder.addNode(cancer);
    builder.addNode(lungCancer);

    EFOTermResolver resolver = new EFOTermResolver(builder.build(), "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: Disease doc should have cancer as expansion
    try (IndexReader reader = DirectoryReader.open(directory)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      TopDocs results =
          searcher.search(new TermQuery(new Term(EFOIndexFields.TERM, "disease")), 10);

      assertTrue(results.totalHits.value() > 0);
      StoredFields storedFields = reader.storedFields();
      Document doc = storedFields.document(results.scoreDocs[0].doc);
      String[] expansions = doc.getValues(EFOIndexFields.EFO);

      assertTrue(List.of(expansions).contains("cancer"), "Should expand to child");
    }
  }

  @Test
  void testExpansionDocument_OrganizationalClass_SkipsChildren() throws IOException {
    stubDefaultStopWords();

    // Given: Organizational class (too broad to expand)
    EFONode root = createNode(EFOTermResolver.ROOT_ID, "experimental factor");
    root.setIsOrganizationalClass(true);
    root.getAlternativeTerms().add("factor");

    EFONode disease = createNode("EFO_0000408", "disease");
    root.addChild(disease);

    EFOModel.Builder builder = EFOModel.builder();
    builder.addNode(root);
    builder.addNode(disease);

    EFOTermResolver resolver = new EFOTermResolver(builder.build(), "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: Root doc should NOT have disease as expansion
    try (IndexReader reader = DirectoryReader.open(directory)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      TopDocs results =
          searcher.search(new TermQuery(new Term(EFOIndexFields.TERM, "experimental factor")), 10);

      if (results.totalHits.value() > 0) {
        StoredFields storedFields = reader.storedFields();
        Document doc = storedFields.document(results.scoreDocs[0].doc);
        String[] expansions = doc.getValues(EFOIndexFields.EFO);

        assertFalse(
            List.of(expansions).contains("disease"),
            "Organizational class should not expand to children");
      }
    }
  }

  @Test
  void testFiltering_StopWords_Excluded() throws IOException {
    stubDefaultStopWords();

    // Given: Node with stop word as term
    EFONode stopNode = createNode(EFOTermResolver.ROOT_ID, "the");
    stopNode.getAlternativeTerms().add("valid term");

    EFOModel model = EFOModel.builder().addNode(stopNode).build();
    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: No document created (stop word term)
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertEquals(0, reader.numDocs(), "Stop word terms should not be indexed");
    }
  }

  @Test
  void testFiltering_ShortTerms_Excluded() throws IOException {
    stubDefaultStopWords();

    // Given: Node with valid term + short synonym + valid synonym
    EFONode root = createRoot();
    EFONode node = createNode("EFO_0000311", "cancer");
    node.getAlternativeTerms().add("ca"); // Too short - will be filtered
    node.getAlternativeTerms().add("neoplasm"); // Valid - ensures document is created
    root.addChild(node);

    EFOModel model = EFOModel.builder().addNode(root).addNode(node).build();
    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: Document created but short term excluded
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertTrue(reader.numDocs() > 0, "Should create document with valid synonyms");

      StoredFields storedFields = reader.storedFields();
      Document doc = storedFields.document(0);
      String[] expansions = doc.getValues(EFOIndexFields.EFO);

      assertFalse(List.of(expansions).contains("ca"), "Short terms should be filtered");
      assertTrue(List.of(expansions).contains("neoplasm"), "Valid synonym should be included");
    }
  }

  @Test
  void testFiltering_QualifiedTerms_Excluded() throws IOException {
    stubDefaultStopWords();

    // Given: Node with qualified synonyms
    EFONode root = createRoot();
    EFONode node = createNode("EFO_0000311", "cancer");
    node.getAlternativeTerms().add("neoplasm (NOS)"); // NOS = not otherwise specified
    node.getAlternativeTerms().add("tumor [obsolete]");
    node.getAlternativeTerms().add("kidney, adult"); // comma separator
    node.getAlternativeTerms().add("heart / lung"); // slash separator
    node.getAlternativeTerms().add("disease - type"); // dash separator
    node.getAlternativeTerms().add("valid synonym"); // Should be kept
    root.addChild(node);

    EFOModel model = EFOModel.builder().addNode(root).addNode(node).build();
    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: Only valid synonym included
    try (IndexReader reader = DirectoryReader.open(directory)) {
      StoredFields storedFields = reader.storedFields();
      Document doc = storedFields.document(0);
      String[] expansions = doc.getValues(EFOIndexFields.EFO);
      List<String> expansionList = List.of(expansions);

      assertFalse(expansionList.contains("neoplasm (nos)"), "NOS terms should be filtered");
      assertFalse(expansionList.contains("tumor [obsolete]"), "Obsolete terms should be filtered");
      assertFalse(
          expansionList.contains("kidney, adult"), "Comma-separated terms should be filtered");
      assertFalse(expansionList.contains("heart / lung"), "Slash terms should be filtered");
      assertFalse(
          expansionList.contains("disease - type"), "Dash-separated terms should be filtered");
      assertTrue(expansionList.contains("valid synonym"), "Valid synonym should be kept");
    }
  }

  @Test
  void testExpansionDocument_SkippedIfNoExpansion() throws IOException {
    stubDefaultStopWords();

    // Given: Node with only primary term (no synonyms/children)
    EFONode node = createNode(EFOTermResolver.ROOT_ID, "cancer");
    // No alternatives, no children

    EFOModel model = EFOModel.builder().addNode(node).build();
    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: No document (no expansion value)
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertEquals(0, reader.numDocs(), "Nodes without expansion should not be indexed");
    }
  }

  @Test
  void testExpansionDocument_IndexedIfSynonymExists() throws IOException {
    stubDefaultStopWords();

    // Given: Node with primary + synonym (minimal expansion)
    EFONode root = createRoot();
    EFONode node = createNode("EFO_0000311", "cancer");
    node.getAlternativeTerms().add("neoplasm");
    root.addChild(node);

    EFOModel model = EFOModel.builder().addNode(root).addNode(node).build();
    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: Document created (2 searchable terms)
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertEquals(1, reader.numDocs(), "Node with synonym should be indexed");
    }
  }

  @Test
  void testExpansionDocument_IndexedIfChildExists() throws IOException {
    stubDefaultStopWords();

    // Given: Node with primary + child (no synonyms)
    EFONode parent = createNode(EFOTermResolver.ROOT_ID, "disease");
    EFONode child = createNode("EFO_0000311", "cancer");
    parent.addChild(child);

    EFOModel.Builder builder = EFOModel.builder();
    builder.addNode(parent);
    builder.addNode(child);

    EFOTermResolver resolver = new EFOTermResolver(builder.build(), "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: Parent indexed (1 term + 1 child)
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertTrue(reader.numDocs() > 0, "Node with child should be indexed");
    }
  }

  @Test
  void testFieldCleaning_RemovesSpecialCharacters() throws IOException {
    stubDefaultStopWords();

    // Given: model must include root node
    EFONode root = createRoot();

    EFONode node = createNode("EFO_0000311", "cancer#test");
    node.getAlternativeTerms().add("neo@plasm!");
    root.addChild(node);

    EFOModel model = EFOModel.builder().addNode(root).addNode(node).build();

    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: Special chars replaced with spaces
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertTrue(reader.numDocs() > 0, "Should create at least one document");

      StoredFields storedFields = reader.storedFields();
      Document doc = storedFields.document(0);
      String[] terms = doc.getValues(EFOIndexFields.TERM);

      assertTrue(List.of(terms).contains("cancer test"), "# should be replaced");
      assertTrue(List.of(terms).contains("neo plasm "), "Special chars should be replaced");
    }
  }

  @Test
  void testFieldCleaning_PreservesHyphens() throws IOException {
    stubDefaultStopWords();

    // Given: model must include the resolver root
    EFONode root = createRoot();

    EFONode node = createNode("EFO_0000311", "cancer");
    node.getAlternativeTerms().add("t-cell");
    root.addChild(node);

    EFOModel model = EFOModel.builder().addNode(root).addNode(node).build();

    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: Hyphens preserved
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertTrue(reader.numDocs() > 0, "Expected an expansion document to be created");

      StoredFields storedFields = reader.storedFields();
      Document doc = storedFields.document(0);
      String[] expansions = doc.getValues(EFOIndexFields.EFO);

      assertTrue(List.of(expansions).contains("t-cell"), "Hyphens should be preserved");
    }
  }

  @Test
  void testRecursiveIndexing_ProcessesEntireHierarchy() throws IOException {
    stubDefaultStopWords();

    // Given: 3-level hierarchy
    EFONode root = createNode(EFOTermResolver.ROOT_ID, "root");
    root.getAlternativeTerms().add("root-syn");

    EFONode level1 = createNode("EFO_0000002", "level1");
    level1.getAlternativeTerms().add("l1-syn");

    EFONode level2 = createNode("EFO_0000003", "level2");
    level2.getAlternativeTerms().add("l2-syn");

    root.addChild(level1);
    level1.addChild(level2);

    EFOModel.Builder builder = EFOModel.builder();
    builder.addNode(root);
    builder.addNode(level1);
    builder.addNode(level2);

    EFOTermResolver resolver = new EFOTermResolver(builder.build(), "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: All 3 nodes indexed
    try (IndexReader reader = DirectoryReader.open(directory)) {
      assertEquals(3, reader.numDocs(), "All hierarchy levels should be indexed");
    }
  }

  @Test
  void testCaseInsensitivity_AllFieldsLowercase() throws IOException {
    stubDefaultStopWords();

    // Given: Node with mixed case
    EFONode root = createRoot();
    EFONode node = createNode("EFO_0000311", "Cancer");
    node.getAlternativeTerms().add("Neoplasm");
    root.addChild(node);

    EFOModel model = EFOModel.builder().addNode(root).addNode(node).build();
    EFOTermResolver resolver = new EFOTermResolver(model, "test");

    // When
    indexer.buildIndex(resolver, writer);
    writer.commit();
    writer.close();

    // Then: All lowercase
    try (IndexReader reader = DirectoryReader.open(directory)) {
      StoredFields storedFields = reader.storedFields();
      Document doc = storedFields.document(0);
      String[] terms = doc.getValues(EFOIndexFields.TERM);

      assertTrue(List.of(terms).contains("cancer"), "Should be lowercase");
      assertTrue(List.of(terms).contains("neoplasm"), "Should be lowercase");
    }
  }

  // Helper methods
  private EFONode createNode(String id, String term) {
    return new EFONode(id, term);
  }

  /**
   * Creates root node for tests. Marked as organizational class with no synonyms so it won't be
   * indexed itself, but recursion still visits its children.
   */
  private EFONode createRoot() {
    EFONode root = new EFONode(EFOTermResolver.ROOT_ID, "efo-root");
    root.setIsOrganizationalClass(true);
    return root;
  }

  private void stubDefaultStopWords() {
    Set<String> stopWords = new HashSet<>(List.of("the", "and", "or", "a"));
    when(efoConfig.getStopWordsSet()).thenReturn(stopWords);
  }
}
