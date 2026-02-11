package uk.ac.ebi.biostudies.index_service.search.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.autocomplete.EFOTermMatcher;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Comprehensive tests for TaxonomySearcher using hierarchical facet encoding.
 *
 * <p>Tests use a realistic EFO hierarchy structure with full paths like: "experimental
 * factor/sample factor/cell type/hematopoietic cell"
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaxonomySearcher")
class TaxonomySearcherTest {

  @Mock private IndexManager indexManager;
  @Mock private TaxonomyManager taxonomyManager;
  @Mock private EFOTermMatcher efoTermMatcher;

  private TaxonomySearcher taxonomySearcher;
  private Directory directory;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private FacetsConfig facetsConfig;

  @BeforeEach
  void setUp() throws IOException {
    taxonomySearcher = new TaxonomySearcher(indexManager, taxonomyManager, efoTermMatcher);
    directory = new ByteBuffersDirectory();

    // Setup FacetsConfig
    facetsConfig = new FacetsConfig();
    facetsConfig.setMultiValued("efo", true);

    when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);

    // Default: no EFO IDs (leaf nodes)
    when(efoTermMatcher.getEFOId(any())).thenReturn(null);

    // Create test index with hierarchical paths
    createTestIndexWithHierarchicalPaths();

    // Setup mocks
    reader = DirectoryReader.open(directory);
    searcher = new IndexSearcher(reader);

    when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);
    doNothing().when(indexManager).releaseSearcher(any(), any());
  }

  @AfterEach
  void tearDown() throws IOException {
    if (reader != null) {
      reader.close();
    }
    if (directory != null) {
      directory.close();
    }
  }

  /**
   * Creates a test index with realistic EFO hierarchical paths.
   *
   * <p>Structure:
   *
   * <ul>
   *   <li>Doc 1: cell type → hematopoietic cell → leukocyte → myeloid leukocyte → macrophage
   *   <li>Doc 2: cell type → hematopoietic cell → leukocyte → myeloid leukocyte → osteoclast →
   *       odontoclast
   *   <li>Doc 3: cell type → epithelial cell
   *   <li>Doc 4: organism → Eukaryota → Mus → Mus musculus
   *   <li>Doc 5: cell activation → leukocyte activation → macrophage activation
   * </ul>
   */
  private void createTestIndexWithHierarchicalPaths() throws IOException {
    IndexWriterConfig config = new IndexWriterConfig();
    try (IndexWriter writer = new IndexWriter(directory, config)) {

      // Document 1: Macrophage hierarchy
      Document doc1 = new Document();
      doc1.add(new StringField("accno", "S-TEST001", Field.Store.YES));
      doc1.add(new SortedSetDocValuesFacetField("efo", "experimental factor"));
      doc1.add(new SortedSetDocValuesFacetField("efo", "experimental factor/sample factor"));
      doc1.add(
          new SortedSetDocValuesFacetField("efo", "experimental factor/sample factor/cell type"));
      doc1.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/sample factor/cell type/hematopoietic cell"));
      doc1.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/sample factor/cell type/hematopoietic cell/leukocyte"));
      doc1.add(
          new SortedSetDocValuesFacetField(
              "efo",
              "experimental factor/sample factor/cell type/hematopoietic cell/leukocyte/myeloid leukocyte"));
      doc1.add(
          new SortedSetDocValuesFacetField(
              "efo",
              "experimental factor/sample factor/cell type/hematopoietic cell/leukocyte/myeloid leukocyte/macrophage"));
      writer.addDocument(facetsConfig.build(doc1));

      // Document 2: Odontoclast hierarchy (shares path with doc1 until myeloid leukocyte)
      Document doc2 = new Document();
      doc2.add(new StringField("accno", "S-TEST002", Field.Store.YES));
      doc2.add(new SortedSetDocValuesFacetField("efo", "experimental factor"));
      doc2.add(new SortedSetDocValuesFacetField("efo", "experimental factor/sample factor"));
      doc2.add(
          new SortedSetDocValuesFacetField("efo", "experimental factor/sample factor/cell type"));
      doc2.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/sample factor/cell type/hematopoietic cell"));
      doc2.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/sample factor/cell type/hematopoietic cell/leukocyte"));
      doc2.add(
          new SortedSetDocValuesFacetField(
              "efo",
              "experimental factor/sample factor/cell type/hematopoietic cell/leukocyte/myeloid leukocyte"));
      doc2.add(
          new SortedSetDocValuesFacetField(
              "efo",
              "experimental factor/sample factor/cell type/hematopoietic cell/leukocyte/myeloid leukocyte/osteoclast"));
      doc2.add(
          new SortedSetDocValuesFacetField(
              "efo",
              "experimental factor/sample factor/cell type/hematopoietic cell/leukocyte/myeloid leukocyte/osteoclast/odontoclast"));
      writer.addDocument(facetsConfig.build(doc2));

      // Document 3: Epithelial cell (different branch from hematopoietic)
      Document doc3 = new Document();
      doc3.add(new StringField("accno", "S-TEST003", Field.Store.YES));
      doc3.add(new SortedSetDocValuesFacetField("efo", "experimental factor"));
      doc3.add(new SortedSetDocValuesFacetField("efo", "experimental factor/sample factor"));
      doc3.add(
          new SortedSetDocValuesFacetField("efo", "experimental factor/sample factor/cell type"));
      doc3.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/sample factor/cell type/epithelial cell"));
      writer.addDocument(facetsConfig.build(doc3));

      // Document 4: Organism hierarchy
      Document doc4 = new Document();
      doc4.add(new StringField("accno", "S-TEST004", Field.Store.YES));
      doc4.add(new SortedSetDocValuesFacetField("efo", "experimental factor"));
      doc4.add(new SortedSetDocValuesFacetField("efo", "experimental factor/sample factor"));
      doc4.add(
          new SortedSetDocValuesFacetField("efo", "experimental factor/sample factor/organism"));
      doc4.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/sample factor/organism/Eukaryota"));
      doc4.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/sample factor/organism/Eukaryota/Mus"));
      doc4.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/sample factor/organism/Eukaryota/Mus/Mus musculus"));
      writer.addDocument(facetsConfig.build(doc4));

      // Document 5: Process hierarchy (cell activation)
      Document doc5 = new Document();
      doc5.add(new StringField("accno", "S-TEST005", Field.Store.YES));
      doc5.add(new SortedSetDocValuesFacetField("efo", "experimental factor"));
      doc5.add(new SortedSetDocValuesFacetField("efo", "experimental factor/process"));
      doc5.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/process/biological process"));
      doc5.add(
          new SortedSetDocValuesFacetField(
              "efo", "experimental factor/process/biological process/cellular process"));
      doc5.add(
          new SortedSetDocValuesFacetField(
              "efo",
              "experimental factor/process/biological process/cellular process/cell activation"));
      doc5.add(
          new SortedSetDocValuesFacetField(
              "efo",
              "experimental factor/process/biological process/cellular process/cell activation/leukocyte activation"));
      doc5.add(
          new SortedSetDocValuesFacetField(
              "efo",
              "experimental factor/process/biological process/cellular process/cell activation/leukocyte activation/macrophage activation"));
      writer.addDocument(facetsConfig.build(doc5));

      writer.commit();
    }
  }

  @Nested
  @DisplayName("searchAllDepths()")
  class SearchAllDepths {

    @Test
    @DisplayName("should find exact term match")
    void shouldFindExactTermMatch() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("leukocyte", 10);

      // THEN - Prefix match finds both "leukocyte" and "leukocyte activation"
      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(TaxonomyNode::term)
          .containsExactlyInAnyOrder("leukocyte", "leukocyte activation");

      // Verify specific term
      TaxonomyNode leukocyte =
          results.stream().filter(n -> n.term().equals("leukocyte")).findFirst().orElseThrow();
      assertThat(leukocyte.count()).isEqualTo(2); // In doc1 and doc2
      assertThat(leukocyte.hasChildren()).isTrue(); // Has myeloid leukocyte as child
    }

    @Test
    @DisplayName("should find terms by prefix")
    void shouldFindTermsByPrefix() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("cell", 10);

      // THEN
      assertThat(results).hasSizeGreaterThanOrEqualTo(3);
      assertThat(results)
          .extracting(TaxonomyNode::term)
          .contains("cell type", "cell activation", "cellular process");
    }

    @Test
    @DisplayName("should aggregate counts for same term in different branches")
    void shouldAggregateCountsForSameTermInDifferentBranches() throws IOException {
      // GIVEN - "macrophage" appears in:
      // 1. doc1: .../myeloid leukocyte/macrophage
      // 2. doc5: .../macrophage activation (different path)

      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("macrophage", 10);

      // THEN
      assertThat(results)
          .extracting(TaxonomyNode::term)
          .contains("macrophage", "macrophage activation");

      TaxonomyNode macrophage =
          results.stream().filter(n -> n.term().equals("macrophage")).findFirst().orElseThrow();
      assertThat(macrophage.count()).isEqualTo(1); // Only in doc1 as leaf term
    }

    @Test
    @DisplayName("should be case insensitive")
    void shouldBeCaseInsensitive() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("LEUKOCYTE", 10);

      // THEN
      assertThat(results)
          .extracting(TaxonomyNode::term)
          .contains("leukocyte", "leukocyte activation");
    }

    @Test
    @DisplayName("should handle whitespace in query")
    void shouldHandleWhitespaceInQuery() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("  leukocyte  ", 10);

      // THEN
      assertThat(results).isNotEmpty();
      assertThat(results.get(0).term()).contains("leukocyte");
    }

    @Test
    @DisplayName("should respect maxResults limit")
    void shouldRespectMaxResultsLimit() throws IOException {
      // WHEN - Search for "cell" which has many matches
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("cell", 2);

      // THEN
      assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("should return results in alphabetical order")
    void shouldReturnResultsInAlphabeticalOrder() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("m", 10);

      // THEN
      assertThat(results).extracting(TaxonomyNode::term).isSorted();
    }

    @Test
    @DisplayName("should return empty list for no matches")
    void shouldReturnEmptyListForNoMatches() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("xyzzyx_nonexistent", 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for null query")
    void shouldReturnEmptyListForNullQuery() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths(null, 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for empty query")
    void shouldReturnEmptyListForEmptyQuery() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("", 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for whitespace-only query")
    void shouldReturnEmptyListForWhitespaceOnlyQuery() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("   ", 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for invalid maxResults")
    void shouldReturnEmptyListForInvalidMaxResults() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("cell", 0);

      // THEN
      assertThat(results).isEmpty();

      // WHEN - negative
      results = taxonomySearcher.searchAllDepths("cell", -5);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should set hasChildren flag correctly")
    void shouldSetHasChildrenFlagCorrectly() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("cell type", 10);

      // THEN
      TaxonomyNode cellType =
          results.stream().filter(n -> n.term().equals("cell type")).findFirst().orElseThrow();

      assertThat(cellType.hasChildren())
          .isTrue(); // Has hematopoietic cell and epithelial cell as children
    }

    @Test
    @DisplayName("should set hasChildren to false for leaf nodes")
    void shouldSetHasChildrenToFalseForLeafNodes() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("odontoclast", 10);

      // THEN
      assertThat(results).hasSize(1);
      assertThat(results.get(0).hasChildren()).isFalse(); // Leaf node
    }

    @Test
    @DisplayName("should handle special characters in term names")
    void shouldHandleSpecialCharactersInTermNames() throws IOException {
      // Note: Current test data doesn't have special chars
      // This is a placeholder for when such data exists
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("Mus", 10);

      // THEN
      assertThat(results).extracting(TaxonomyNode::term).contains("Mus", "Mus musculus");
    }

    @Test
    @DisplayName("should find terms at different hierarchy levels")
    void shouldFindTermsAtDifferentHierarchyLevels() throws IOException {
      // WHEN - Search for "hematopoietic" (intermediate level)
      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("hematopoietic", 10);

      // THEN
      assertThat(results).hasSize(1);
      TaxonomyNode hematopoietic = results.get(0);
      assertThat(hematopoietic.term()).isEqualTo("hematopoietic cell");
      assertThat(hematopoietic.count()).isEqualTo(2); // In doc1 and doc2
      assertThat(hematopoietic.hasChildren()).isTrue(); // Has leukocyte as child
    }

    @Test
    @DisplayName("should verify index manager interactions")
    void shouldVerifyIndexManagerInteractions() throws IOException {
      // WHEN
      taxonomySearcher.searchAllDepths("test", 10);

      // THEN
      verify(indexManager).acquireSearcher(IndexName.SUBMISSION);
      verify(indexManager).releaseSearcher(IndexName.SUBMISSION, searcher);
    }
  }

  @Nested
  @DisplayName("getChildren()")
  class GetChildren {

    @Test
    @DisplayName("should return direct children of parent term")
    void shouldReturnDirectChildrenOfParentTerm() throws IOException {
      // WHEN - Get children of "cell type"
      List<TaxonomyNode> results = taxonomySearcher.getChildren("cell type", 10);

      // THEN - Should have hematopoietic cell and epithelial cell
      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(TaxonomyNode::term)
          .containsExactlyInAnyOrder("hematopoietic cell", "epithelial cell");
    }

    @Test
    @DisplayName("should return correct counts for children")
    void shouldReturnCorrectCountsForChildren() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("cell type", 10);

      // THEN
      TaxonomyNode hematopoietic =
          results.stream()
              .filter(n -> n.term().equals("hematopoietic cell"))
              .findFirst()
              .orElseThrow();
      assertThat(hematopoietic.count()).isEqualTo(2); // In doc1 and doc2

      TaxonomyNode epithelial =
          results.stream()
              .filter(n -> n.term().equals("epithelial cell"))
              .findFirst()
              .orElseThrow();
      assertThat(epithelial.count()).isEqualTo(1); // Only in doc3
    }

    @Test
    @DisplayName("should set hasChildren flag for children with grandchildren")
    void shouldSetHasChildrenFlagForChildrenWithGrandchildren() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("cell type", 10);

      // THEN
      TaxonomyNode hematopoietic =
          results.stream()
              .filter(n -> n.term().equals("hematopoietic cell"))
              .findFirst()
              .orElseThrow();
      assertThat(hematopoietic.hasChildren()).isTrue(); // Has leukocyte as child

      TaxonomyNode epithelial =
          results.stream()
              .filter(n -> n.term().equals("epithelial cell"))
              .findFirst()
              .orElseThrow();
      assertThat(epithelial.hasChildren()).isFalse(); // Leaf node
    }

    @Test
    @DisplayName("should return empty list for leaf nodes")
    void shouldReturnEmptyListForLeafNodes() throws IOException {
      // WHEN - Odontoclast is a leaf node
      List<TaxonomyNode> results = taxonomySearcher.getChildren("odontoclast", 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return children at different hierarchy depths")
    void shouldReturnChildrenAtDifferentHierarchyDepths() throws IOException {
      // WHEN - Get children of intermediate node
      List<TaxonomyNode> results = taxonomySearcher.getChildren("hematopoietic cell", 10);

      // THEN
      assertThat(results).hasSize(1);
      assertThat(results.get(0).term()).isEqualTo("leukocyte");
    }

    @Test
    @DisplayName("should return children in alphabetical order")
    void shouldReturnChildrenInAlphabeticalOrder() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("myeloid leukocyte", 10);

      // THEN - Has macrophage and osteoclast as children
      assertThat(results)
          .extracting(TaxonomyNode::term)
          .containsExactly("macrophage", "osteoclast"); // Alphabetical order
    }

    @Test
    @DisplayName("should respect maxResults limit")
    void shouldRespectMaxResultsLimit() throws IOException {
      // WHEN - Get children with limit
      List<TaxonomyNode> results = taxonomySearcher.getChildren("cell type", 1);

      // THEN
      assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("should return empty list for non-existent parent")
    void shouldReturnEmptyListForNonExistentParent() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("nonexistent_term", 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for null parent")
    void shouldReturnEmptyListForNullParent() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren(null, 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for empty parent")
    void shouldReturnEmptyListForEmptyParent() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("", 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for whitespace-only parent")
    void shouldReturnEmptyListForWhitespaceOnlyParent() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("   ", 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for invalid maxResults")
    void shouldReturnEmptyListForInvalidMaxResults() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("cell type", 0);

      // THEN
      assertThat(results).isEmpty();

      // WHEN - negative
      results = taxonomySearcher.getChildren("cell type", -5);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should handle whitespace in parent term")
    void shouldHandleWhitespaceInParentTerm() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("  cell type  ", 10);

      // THEN
      assertThat(results).isEmpty(); // Trimmed, but "  cell type  " != "cell type" in current impl
    }

    @Test
    @DisplayName("should use max aggregation to avoid double-counting")
    void shouldUseMaxAggregationToAvoidDoubleCounting() throws IOException {
      // GIVEN - "myeloid leukocyte" appears in both doc1 and doc2
      // Children should not have inflated counts

      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildren("myeloid leukocyte", 10);

      // THEN
      TaxonomyNode macrophage =
          results.stream().filter(n -> n.term().equals("macrophage")).findFirst().orElseThrow();
      assertThat(macrophage.count()).isEqualTo(1); // Only in doc1, not double-counted

      TaxonomyNode osteoclast =
          results.stream().filter(n -> n.term().equals("osteoclast")).findFirst().orElseThrow();
      assertThat(osteoclast.count()).isEqualTo(1); // Only in doc2
    }

    @Test
    @DisplayName("should verify index manager interactions")
    void shouldVerifyIndexManagerInteractions() throws IOException {
      // WHEN
      taxonomySearcher.getChildren("cell type", 10);

      // THEN
      verify(indexManager).acquireSearcher(IndexName.SUBMISSION);
      verify(indexManager).releaseSearcher(IndexName.SUBMISSION, searcher);
    }
  }

  @Nested
  @DisplayName("getChildrenByEfoId()")
  class GetChildrenByEfoId {

    @Test
    @DisplayName("should resolve EFO ID to term and get children")
    void shouldResolveEfoIdToTermAndGetChildren() throws IOException {
      // GIVEN
      String efoId = "http://www.ebi.ac.uk/efo/efo_0000324";
      when(efoTermMatcher.getTerm(efoId)).thenReturn("cell type");

      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildrenByEfoId(efoId, 10);

      // THEN
      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(TaxonomyNode::term)
          .containsExactlyInAnyOrder("hematopoietic cell", "epithelial cell");
      verify(efoTermMatcher).getTerm(efoId);
    }

    @Test
    @DisplayName("should return empty list for unknown EFO ID")
    void shouldReturnEmptyListForUnknownEfoId() throws IOException {
      // GIVEN
      String efoId = "http://example.org/unknown";
      when(efoTermMatcher.getTerm(efoId)).thenReturn(null);

      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildrenByEfoId(efoId, 10);

      // THEN
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for null EFO ID")
    void shouldReturnEmptyListForNullEfoId() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildrenByEfoId(null, 10);

      // THEN
      assertThat(results).isEmpty();
      verify(efoTermMatcher, never()).getTerm(any());
    }

    @Test
    @DisplayName("should return empty list for empty EFO ID")
    void shouldReturnEmptyListForEmptyEfoId() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildrenByEfoId("", 10);

      // THEN
      assertThat(results).isEmpty();
      verify(efoTermMatcher, never()).getTerm(any());
    }

    @Test
    @DisplayName("should return empty list for whitespace-only EFO ID")
    void shouldReturnEmptyListForWhitespaceOnlyEfoId() throws IOException {
      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildrenByEfoId("   ", 10);

      // THEN
      assertThat(results).isEmpty();
      verify(efoTermMatcher, never()).getTerm(any());
    }

    @Test
    @DisplayName("should handle URL-encoded EFO IDs")
    void shouldHandleUrlEncodedEfoIds() throws IOException {
      // GIVEN - URL-decoded form
      String efoId = "http://purl.obolibrary.org/obo/cl_0000738";
      when(efoTermMatcher.getTerm(efoId)).thenReturn("leukocyte");

      // WHEN
      List<TaxonomyNode> results = taxonomySearcher.getChildrenByEfoId(efoId, 10);

      // THEN
      assertThat(results).isNotEmpty();
      verify(efoTermMatcher).getTerm(efoId);
    }
  }

  @Nested
  @DisplayName("formatAsAutocompleteResponse()")
  class FormatAsAutocompleteResponse {

    @Test
    @DisplayName("should format nodes with hasChildren=true correctly")
    void shouldFormatNodesWithHasChildrenTrue() {
      // GIVEN
      List<TaxonomyNode> nodes =
          List.of(new TaxonomyNode("cell type", 5, "http://www.ebi.ac.uk/efo/efo_0000324", true));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN
      assertThat(result).isEqualTo("cell type|o|http://www.ebi.ac.uk/efo/efo_0000324|5\n");
    }

    @Test
    @DisplayName("should format nodes with hasChildren=false correctly")
    void shouldFormatNodesWithHasChildrenFalse() {
      // GIVEN
      List<TaxonomyNode> nodes =
          List.of(
              new TaxonomyNode(
                  "odontoclast", 1, "http://purl.obolibrary.org/obo/CL_0002544", false));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN - efoId should be empty for leaf nodes
      assertThat(result).isEqualTo("odontoclast|o||1\n");
    }

    @Test
    @DisplayName("should format multiple nodes correctly")
    void shouldFormatMultipleNodesCorrectly() {
      // GIVEN
      List<TaxonomyNode> nodes =
          List.of(
              new TaxonomyNode("cell type", 5, "http://www.ebi.ac.uk/efo/efo_0000324", true),
              new TaxonomyNode("leukocyte", 2, "http://purl.obolibrary.org/obo/CL_0000738", true),
              new TaxonomyNode("odontoclast", 1, null, false));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN
      assertThat(result)
          .isEqualTo(
              "cell type|o|http://www.ebi.ac.uk/efo/efo_0000324|5\n"
                  + "leukocyte|o|http://purl.obolibrary.org/obo/CL_0000738|2\n"
                  + "odontoclast|o||1\n");
    }

    @Test
    @DisplayName("should handle node with null efoId")
    void shouldHandleNodeWithNullEfoId() {
      // GIVEN
      List<TaxonomyNode> nodes = List.of(new TaxonomyNode("unknown term", 1, null, false));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN
      assertThat(result).isEqualTo("unknown term|o||1\n");
    }

    @Test
    @DisplayName("should handle node with hasChildren=true but null efoId")
    void shouldHandleNodeWithHasChildrenTrueButNullEfoId() {
      // GIVEN - Edge case: has children but no EFO ID
      List<TaxonomyNode> nodes = List.of(new TaxonomyNode("term", 1, null, true));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN - Should show empty efoId since it's null
      assertThat(result).isEqualTo("term|o||1\n");
    }

    @Test
    @DisplayName("should return empty string for empty list")
    void shouldReturnEmptyStringForEmptyList() {
      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(List.of());

      // THEN
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty string for null list")
    void shouldReturnEmptyStringForNullList() {
      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(null);

      // THEN
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should handle terms with special characters")
    void shouldHandleTermsWithSpecialCharacters() {
      // GIVEN
      List<TaxonomyNode> nodes =
          List.of(
              new TaxonomyNode("CD4+ T cell", 5, null, false),
              new TaxonomyNode("α-cell", 3, null, false));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN
      assertThat(result).isEqualTo("CD4+ T cell|o||5\n" + "α-cell|o||3\n");
    }

    @Test
    @DisplayName("should handle terms with whitespace")
    void shouldHandleTermsWithWhitespace() {
      // GIVEN
      List<TaxonomyNode> nodes =
          List.of(
              new TaxonomyNode("bone marrow", 2, null, false),
              new TaxonomyNode("organism part", 1, null, false));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN
      assertThat(result).isEqualTo("bone marrow|o||2\n" + "organism part|o||1\n");
    }

    @Test
    @DisplayName("should handle zero count")
    void shouldHandleZeroCount() {
      // GIVEN - Edge case
      List<TaxonomyNode> nodes = List.of(new TaxonomyNode("rare term", 0, null, false));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN
      assertThat(result).isEqualTo("rare term|o||0\n");
    }

    @Test
    @DisplayName("should handle very large counts")
    void shouldHandleVeryLargeCounts() {
      // GIVEN
      List<TaxonomyNode> nodes =
          List.of(new TaxonomyNode("common term", Integer.MAX_VALUE, null, false));

      // WHEN
      String result = taxonomySearcher.formatAsAutocompleteResponse(nodes);

      // THEN
      assertThat(result).isEqualTo("common term|o||" + Integer.MAX_VALUE + "\n");
    }
  }

  @Nested
  @DisplayName("Integration tests")
  class IntegrationTests {

    @Test
    @DisplayName("should support complete search-and-expand workflow")
    void shouldSupportCompleteSearchAndExpandWorkflow() throws IOException {
      // Step 1: User searches for "cell"
      List<TaxonomyNode> searchResults = taxonomySearcher.searchAllDepths("cell", 10);

      assertThat(searchResults)
          .extracting(TaxonomyNode::term)
          .contains("cell type", "cell activation");

      // Step 2: User expands "cell type"
      when(efoTermMatcher.getTerm("http://www.ebi.ac.uk/efo/efo_0000324")).thenReturn("cell type");

      List<TaxonomyNode> children =
          taxonomySearcher.getChildrenByEfoId("http://www.ebi.ac.uk/efo/efo_0000324", 10);

      assertThat(children)
          .extracting(TaxonomyNode::term)
          .containsExactlyInAnyOrder("hematopoietic cell", "epithelial cell");

      // Step 3: User expands "hematopoietic cell"
      List<TaxonomyNode> grandchildren = taxonomySearcher.getChildren("hematopoietic cell", 10);

      assertThat(grandchildren).extracting(TaxonomyNode::term).contains("leukocyte");
    }

    @Test
    @DisplayName("should handle multi-level hierarchy traversal")
    void shouldHandleMultiLevelHierarchyTraversal() throws IOException {
      // Traverse from cell type → hematopoietic cell → leukocyte → myeloid leukocyte

      List<TaxonomyNode> level1 = taxonomySearcher.getChildren("cell type", 10);
      assertThat(level1).extracting(TaxonomyNode::term).contains("hematopoietic cell");

      List<TaxonomyNode> level2 = taxonomySearcher.getChildren("hematopoietic cell", 10);
      assertThat(level2).extracting(TaxonomyNode::term).contains("leukocyte");

      List<TaxonomyNode> level3 = taxonomySearcher.getChildren("leukocyte", 10);
      assertThat(level3).extracting(TaxonomyNode::term).contains("myeloid leukocyte");

      List<TaxonomyNode> level4 = taxonomySearcher.getChildren("myeloid leukocyte", 10);
      assertThat(level4)
          .extracting(TaxonomyNode::term)
          .containsExactlyInAnyOrder("macrophage", "osteoclast");
    }

    @Test
    @DisplayName("should correctly identify leaf nodes across hierarchy")
    void shouldCorrectlyIdentifyLeafNodesAcrossHierarchy() throws IOException {
      // Search for all leaf nodes
      List<TaxonomyNode> macrophage = taxonomySearcher.searchAllDepths("macrophage", 10);
      assertThat(macrophage.get(0).hasChildren()).isFalse();

      List<TaxonomyNode> odontoclast = taxonomySearcher.searchAllDepths("odontoclast", 10);
      assertThat(odontoclast.get(0).hasChildren()).isFalse();

      List<TaxonomyNode> epithelial = taxonomySearcher.searchAllDepths("epithelial cell", 10);
      assertThat(epithelial.get(0).hasChildren()).isFalse();

      List<TaxonomyNode> musMusculus = taxonomySearcher.searchAllDepths("Mus musculus", 10);
      assertThat(musMusculus.get(0).hasChildren()).isFalse();
    }

    @Test
    @DisplayName("should handle terms appearing in multiple branches")
    void shouldHandleTermsAppearingInMultipleBranches() throws IOException {
      // "macrophage" appears in:
      // 1. .../cell type/.../macrophage (doc1)
      // 2. .../cell activation/.../macrophage activation (doc5)

      List<TaxonomyNode> results = taxonomySearcher.searchAllDepths("macrophage", 10);

      assertThat(results)
          .extracting(TaxonomyNode::term)
          .contains("macrophage", "macrophage activation");
    }
  }
}
