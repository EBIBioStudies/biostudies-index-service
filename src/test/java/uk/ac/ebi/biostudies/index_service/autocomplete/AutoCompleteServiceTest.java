package uk.ac.ebi.biostudies.index_service.autocomplete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearcher;
import uk.ac.ebi.biostudies.index_service.search.taxonomy.TaxonomyNode;
import uk.ac.ebi.biostudies.index_service.search.taxonomy.TaxonomySearcher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoCompleteService")
class AutoCompleteServiceTest {

  @Mock
  private TaxonomySearcher taxonomySearcher;

  @Mock
  private EFOSearcher efoSearcher;

  private AutoCompleteService autoCompleteService;

  @BeforeEach
  void setUp() {
    autoCompleteService = new AutoCompleteService(efoSearcher, taxonomySearcher);

    // Default mock behavior for formatting (lenient because not all tests reach formatting)
    lenient().when(taxonomySearcher.formatAsAutocompleteResponse(anyList()))
        .thenAnswer(invocation -> {
          List<TaxonomyNode> nodes = invocation.getArgument(0);
          if (nodes == null || nodes.isEmpty()) {
            return "";
          }
          StringBuilder result = new StringBuilder();
          for (TaxonomyNode node : nodes) {
            // Format: term|o|efoId|count (with efoId empty if hasChildren is false)
            String efoIdPart = node.hasChildren() && node.efoId() != null ? node.efoId() : "";
            result.append(node.term())
                .append("|o|")
                .append(efoIdPart)
                .append("|")
                .append(node.count())
                .append("\\n");
          }
          return result.toString();
        });
  }

  @Nested
  @DisplayName("getKeywordsWithCounts()")
  class GetKeywordsWithCounts {

    @Test
    @DisplayName("should return formatted results for valid query")
    void shouldReturnFormattedResultsForValidQuery() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("mouse", 5, "http://purl.obolibrary.org/obo/NCBITaxon_10090", true),
          new TaxonomyNode("macrophage", 3, "http://purl.obolibrary.org/obo/CL_0000235", false)
      );
      when(taxonomySearcher.searchAllDepths("mouse", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("mouse", 10);

      // Then
      assertThat(result).isEqualTo(
          "mouse|o|http://purl.obolibrary.org/obo/NCBITaxon_10090|5\\n" +
              "macrophage|o||3\\n"
      );
      verify(taxonomySearcher).searchAllDepths("mouse", 10);
      verify(taxonomySearcher).formatAsAutocompleteResponse(mockNodes);
    }

    @Test
    @DisplayName("should return empty string when no results found")
    void shouldReturnEmptyStringWhenNoResults() throws IOException {
      // Given
      when(taxonomySearcher.searchAllDepths("xyz", 10)).thenReturn(List.of());

      // When
      String result = autoCompleteService.getKeywordsWithCounts("xyz", 10);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher).searchAllDepths("xyz", 10);
    }

    @Test
    @DisplayName("should return empty string for null query")
    void shouldReturnEmptyStringForNullQuery() throws IOException {
      // When
      String result = autoCompleteService.getKeywordsWithCounts(null, 10);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher, never()).searchAllDepths(anyString(), anyInt());
    }

    @Test
    @DisplayName("should return empty string for empty query")
    void shouldReturnEmptyStringForEmptyQuery() throws IOException {
      // When
      String result = autoCompleteService.getKeywordsWithCounts("", 10);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher, never()).searchAllDepths(anyString(), anyInt());
    }

    @Test
    @DisplayName("should return empty string for whitespace-only query")
    void shouldReturnEmptyStringForWhitespaceOnlyQuery() throws IOException {
      // When
      String result = autoCompleteService.getKeywordsWithCounts("   ", 10);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher, never()).searchAllDepths(anyString(), anyInt());
    }

    @Test
    @DisplayName("should cap limit to MAX_LIMIT when exceeded")
    void shouldCapLimitToMaxWhenExceeded() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("test", 1, null, false)
      );
      when(taxonomySearcher.searchAllDepths("test", 200)).thenReturn(mockNodes);

      // When
      autoCompleteService.getKeywordsWithCounts("test", 999);

      // Then
      verify(taxonomySearcher).searchAllDepths("test", 200);
    }

    @Test
    @DisplayName("should use provided limit when below max")
    void shouldUseProvidedLimitWhenBelowMax() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("test", 1, null, false)
      );
      when(taxonomySearcher.searchAllDepths("test", 5)).thenReturn(mockNodes);

      // When
      autoCompleteService.getKeywordsWithCounts("test", 5);

      // Then
      verify(taxonomySearcher).searchAllDepths("test", 5);
    }

    @Test
    @DisplayName("should return empty string when IOException occurs")
    void shouldReturnEmptyStringOnIOException() throws IOException {
      // Given
      when(taxonomySearcher.searchAllDepths("test", 10))
          .thenThrow(new IOException("Index error"));

      // When
      String result = autoCompleteService.getKeywordsWithCounts("test", 10);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher).searchAllDepths("test", 10);
    }

    @Test
    @DisplayName("should preserve result order")
    void shouldPreserveResultOrder() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("aardvark", 1, null, false),
          new TaxonomyNode("bear", 2, null, false),
          new TaxonomyNode("cat", 3, null, false)
      );
      when(taxonomySearcher.searchAllDepths("a", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("a", 10);

      // Then
      assertThat(result).isEqualTo("aardvark|o||1\\nbear|o||2\\ncat|o||3\\n");
    }

    @Test
    @DisplayName("should handle queries with whitespace")
    void shouldHandleQueriesWithWhitespace() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("bone marrow", 2, null, false)
      );
      when(taxonomySearcher.searchAllDepths("bone marrow", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("bone marrow", 10);

      // Then
      assertThat(result).isEqualTo("bone marrow|o||2\\n");
      verify(taxonomySearcher).searchAllDepths("bone marrow", 10);
    }

    @Test
    @DisplayName("should handle special characters in query")
    void shouldHandleSpecialCharactersInQuery() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("CD8+ T cell", 5, null, false)
      );
      when(taxonomySearcher.searchAllDepths("CD8+", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("CD8+", 10);

      // Then
      assertThat(result).isEqualTo("CD8+ T cell|o||5\\n");
      verify(taxonomySearcher).searchAllDepths("CD8+", 10);
    }

    @Test
    @DisplayName("should handle terms with both expandable and non-expandable results")
    void shouldHandleMixedExpandableResults() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("cell", 10, "http://purl.obolibrary.org/obo/CL_0000000", true), // expandable
          new TaxonomyNode("cellular process", 5, null, false) // not expandable
      );
      when(taxonomySearcher.searchAllDepths("cell", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("cell", 10);

      // Then
      assertThat(result).isEqualTo(
          "cell|o|http://purl.obolibrary.org/obo/CL_0000000|10\\n" +
              "cellular process|o||5\\n"
      );
    }

    @Test
    @DisplayName("should handle very long term names")
    void shouldHandleVeryLongTermNames() throws IOException {
      // Given
      String longTerm = "very ".repeat(50) + "long term";
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode(longTerm, 1, null, false)
      );
      when(taxonomySearcher.searchAllDepths("very", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("very", 10);

      // Then
      assertThat(result).startsWith(longTerm);
      verify(taxonomySearcher).searchAllDepths("very", 10);
    }

    @Test
    @DisplayName("should handle zero count results")
    void shouldHandleZeroCountResults() throws IOException {
      // Given - edge case where count is 0 (shouldn't normally happen but defensive)
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("rare term", 0, null, false)
      );
      when(taxonomySearcher.searchAllDepths("rare", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("rare", 10);

      // Then
      assertThat(result).isEqualTo("rare term|o||0\\n");
    }

    @Test
    @DisplayName("should handle high count results")
    void shouldHandleHighCountResults() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("human", 999999, null, false)
      );
      when(taxonomySearcher.searchAllDepths("human", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("human", 10);

      // Then
      assertThat(result).isEqualTo("human|o||999999\\n");
    }
  }

  @Nested
  @DisplayName("getEfoTreeWithCounts()")
  class GetEfoTreeWithCounts {

    @Test
    @DisplayName("should return children for valid EFO ID")
    void shouldReturnChildrenForValidEfoId() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_0000738";
      List<TaxonomyNode> mockChildren = List.of(
          new TaxonomyNode("osteoclast", 5, "http://purl.obolibrary.org/obo/CL_0000092", true),
          new TaxonomyNode("macrophage", 3, null, false)
      );
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(mockChildren);

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEqualTo(
          "osteoclast|o|http://purl.obolibrary.org/obo/CL_0000092|5\\n" +
              "macrophage|o||3\\n"
      );
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 20);
      verify(taxonomySearcher).formatAsAutocompleteResponse(mockChildren);
    }

    @Test
    @DisplayName("should return empty string for leaf node")
    void shouldReturnEmptyStringForLeafNode() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_9999999";
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(List.of());

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 20);
    }

    @Test
    @DisplayName("should return empty string for null EFO ID")
    void shouldReturnEmptyStringForNullEfoId() throws IOException {
      // When
      String result = autoCompleteService.getEfoTreeWithCounts(null, 20);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher, never()).getChildrenByEfoId(anyString(), anyInt());
    }

    @Test
    @DisplayName("should return empty string for empty EFO ID")
    void shouldReturnEmptyStringForEmptyEfoId() throws IOException {
      // When
      String result = autoCompleteService.getEfoTreeWithCounts("", 20);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher, never()).getChildrenByEfoId(anyString(), anyInt());
    }

    @Test
    @DisplayName("should return empty string for whitespace-only EFO ID")
    void shouldReturnEmptyStringForWhitespaceOnlyEfoId() throws IOException {
      // When
      String result = autoCompleteService.getEfoTreeWithCounts("   ", 20);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher, never()).getChildrenByEfoId(anyString(), anyInt());
    }

    @Test
    @DisplayName("should use default limit when limit is zero")
    void shouldUseDefaultLimitWhenZero() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_0000000";
      List<TaxonomyNode> mockChildren = List.of(
          new TaxonomyNode("child", 1, null, false)
      );
      when(taxonomySearcher.getChildrenByEfoId(efoId, 500)).thenReturn(mockChildren);

      // When
      autoCompleteService.getEfoTreeWithCounts(efoId, 0);

      // Then
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 500);
    }

    @Test
    @DisplayName("should use default limit when limit is negative")
    void shouldUseDefaultLimitWhenNegative() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_0000000";
      List<TaxonomyNode> mockChildren = List.of(
          new TaxonomyNode("child", 1, null, false)
      );
      when(taxonomySearcher.getChildrenByEfoId(efoId, 500)).thenReturn(mockChildren);

      // When
      autoCompleteService.getEfoTreeWithCounts(efoId, -5);

      // Then
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 500);
    }

    @Test
    @DisplayName("should use provided limit when positive")
    void shouldUseProvidedLimitWhenPositive() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_0000000";
      List<TaxonomyNode> mockChildren = List.of(
          new TaxonomyNode("child", 1, null, false)
      );
      when(taxonomySearcher.getChildrenByEfoId(efoId, 10)).thenReturn(mockChildren);

      // When
      autoCompleteService.getEfoTreeWithCounts(efoId, 10);

      // Then
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 10);
    }

    @Test
    @DisplayName("should return empty string when IOException occurs")
    void shouldReturnEmptyStringOnIOException() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_0000000";
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20))
          .thenThrow(new IOException("Index error"));

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 20);
    }

    @Test
    @DisplayName("should return empty string for non-existent EFO ID")
    void shouldReturnEmptyStringForNonExistentEfoId() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_9999999";
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(List.of());

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEmpty();
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 20);
    }

    @Test
    @DisplayName("should preserve children order")
    void shouldPreserveChildrenOrder() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_0000000";
      List<TaxonomyNode> mockChildren = List.of(
          new TaxonomyNode("alpha cell", 1, null, false),
          new TaxonomyNode("beta cell", 2, null, false),
          new TaxonomyNode("gamma cell", 3, null, false)
      );
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(mockChildren);

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEqualTo("alpha cell|o||1\\nbeta cell|o||2\\ngamma cell|o||3\\n");
    }

    @Test
    @DisplayName("should handle URL-encoded EFO IDs")
    void shouldHandleUrlEncodedEfoIds() throws IOException {
      // Given - URL-decoded form
      String efoId = "http://purl.obolibrary.org/obo/cl_0000000";
      List<TaxonomyNode> mockChildren = List.of(
          new TaxonomyNode("child", 1, null, false)
      );
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(mockChildren);

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEqualTo("child|o||1\\n");
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 20);
    }

    @Test
    @DisplayName("should handle children with special characters in names")
    void shouldHandleChildrenWithSpecialCharacters() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_0000000";
      List<TaxonomyNode> mockChildren = List.of(
          new TaxonomyNode("CD4+ T cell", 10, null, false),
          new TaxonomyNode("CD8+ T cell", 8, null, false),
          new TaxonomyNode("NK/T cell", 5, null, false)
      );
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(mockChildren);

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).contains("CD4+ T cell|o||10\\n");
      assertThat(result).contains("CD8+ T cell|o||8\\n");
      assertThat(result).contains("NK/T cell|o||5\\n");
    }

    @Test
    @DisplayName("should handle large number of children")
    void shouldHandleLargeNumberOfChildren() throws IOException {
      // Given
      String efoId = "http://purl.obolibrary.org/obo/CL_0000000";
      List<TaxonomyNode> mockChildren = new java.util.ArrayList<>();
      for (int i = 0; i < 100; i++) {
        mockChildren.add(new TaxonomyNode("child_" + i, i, null, false));
      }
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(mockChildren);

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result.split("\\\\n")).hasSize(100);
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 20);
    }
  }

  @Nested
  @DisplayName("Integration scenarios")
  class IntegrationScenarios {

    @Test
    @DisplayName("should support search then expand workflow")
    void shouldSupportSearchThenExpandWorkflow() throws IOException {
      // Given - User searches for "cell"
      List<TaxonomyNode> searchResults = List.of(
          new TaxonomyNode("cell type", 10, "http://www.ebi.ac.uk/efo/efo_0000324", true)
      );
      when(taxonomySearcher.searchAllDepths("cell", 10)).thenReturn(searchResults);

      // When - User performs search
      String searchResult = autoCompleteService.getKeywordsWithCounts("cell", 10);

      // Then - User gets "cell type" with expand button
      assertThat(searchResult).isEqualTo("cell type|o|http://www.ebi.ac.uk/efo/efo_0000324|10\\n");

      // Given - User expands "cell type" to see children
      String efoId = "http://www.ebi.ac.uk/efo/efo_0000324";
      List<TaxonomyNode> children = List.of(
          new TaxonomyNode("hematopoietic cell", 5, "http://purl.obolibrary.org/obo/CL_0000988", true),
          new TaxonomyNode("epithelial cell", 3, null, false)
      );
      when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(children);

      // When - User requests children
      String treeResult = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then - User gets children with correct expand indicators
      assertThat(treeResult).isEqualTo(
          "hematopoietic cell|o|http://purl.obolibrary.org/obo/CL_0000988|5\\n" +
              "epithelial cell|o||3\\n"
      );

      verify(taxonomySearcher).searchAllDepths("cell", 10);
      verify(taxonomySearcher).getChildrenByEfoId(efoId, 20);
    }

    @Test
    @DisplayName("should handle errors gracefully without propagating exceptions")
    void shouldHandleErrorsGracefully() throws IOException {
      // Given
      when(taxonomySearcher.searchAllDepths(anyString(), anyInt()))
          .thenThrow(new IOException("Index corrupted"));
      when(taxonomySearcher.getChildrenByEfoId(anyString(), anyInt()))
          .thenThrow(new IOException("Index corrupted"));

      // When - Both methods should handle exceptions gracefully
      String searchResult = autoCompleteService.getKeywordsWithCounts("test", 10);
      String treeResult = autoCompleteService.getEfoTreeWithCounts("http://test", 20);

      // Then - Both return empty strings without throwing
      assertThat(searchResult).isEmpty();
      assertThat(treeResult).isEmpty();
    }

    @Test
    @DisplayName("should handle multi-level tree expansion")
    void shouldHandleMultiLevelTreeExpansion() throws IOException {
      // Level 1: Search returns "cell type"
      List<TaxonomyNode> level1 = List.of(
          new TaxonomyNode("cell type", 100, "http://www.ebi.ac.uk/efo/efo_0000324", true)
      );
      when(taxonomySearcher.searchAllDepths("cell", 10)).thenReturn(level1);

      // Level 2: Expand "cell type" returns "hematopoietic cell"
      List<TaxonomyNode> level2 = List.of(
          new TaxonomyNode("hematopoietic cell", 50, "http://purl.obolibrary.org/obo/CL_0000988", true)
      );
      when(taxonomySearcher.getChildrenByEfoId("http://www.ebi.ac.uk/efo/efo_0000324", 20))
          .thenReturn(level2);

      // Level 3: Expand "hematopoietic cell" returns "leukocyte"
      List<TaxonomyNode> level3 = List.of(
          new TaxonomyNode("leukocyte", 30, "http://purl.obolibrary.org/obo/CL_0000738", true)
      );
      when(taxonomySearcher.getChildrenByEfoId("http://purl.obolibrary.org/obo/CL_0000988", 20))
          .thenReturn(level3);

      // Execute workflow
      String search = autoCompleteService.getKeywordsWithCounts("cell", 10);
      String expand1 = autoCompleteService.getEfoTreeWithCounts("http://www.ebi.ac.uk/efo/efo_0000324", 20);
      String expand2 = autoCompleteService.getEfoTreeWithCounts("http://purl.obolibrary.org/obo/CL_0000988", 20);

      // Verify
      assertThat(search).contains("cell type");
      assertThat(expand1).contains("hematopoietic cell");
      assertThat(expand2).contains("leukocyte");
    }

    @Test
    @DisplayName("should handle empty results at different levels")
    void shouldHandleEmptyResultsAtDifferentLevels() throws IOException {
      // Given - Search returns results but some nodes have no children
      List<TaxonomyNode> searchResults = List.of(
          new TaxonomyNode("rare cell", 1, "http://example.org/rare", false)
      );
      when(taxonomySearcher.searchAllDepths("rare", 10)).thenReturn(searchResults);
      when(taxonomySearcher.getChildrenByEfoId("http://example.org/rare", 20)).thenReturn(List.of());

      // When
      String searchResult = autoCompleteService.getKeywordsWithCounts("rare", 10);
      String treeResult = autoCompleteService.getEfoTreeWithCounts("http://example.org/rare", 20);

      // Then
      assertThat(searchResult).isEqualTo("rare cell|o||1\\n"); // No expand button (empty efoId)
      assertThat(treeResult).isEmpty(); // No children
    }

    @Test
    @DisplayName("should handle concurrent access patterns")
    void shouldHandleConcurrentAccessPatterns() throws IOException {
      // Given - Simulate concurrent requests with different queries
      List<TaxonomyNode> results1 = List.of(new TaxonomyNode("term1", 1, null, false));
      List<TaxonomyNode> results2 = List.of(new TaxonomyNode("term2", 2, null, false));
      List<TaxonomyNode> results3 = List.of(new TaxonomyNode("term3", 3, null, false));

      when(taxonomySearcher.searchAllDepths("query1", 10)).thenReturn(results1);
      when(taxonomySearcher.searchAllDepths("query2", 10)).thenReturn(results2);
      when(taxonomySearcher.searchAllDepths("query3", 10)).thenReturn(results3);

      // When - Execute concurrent-style calls
      String result1 = autoCompleteService.getKeywordsWithCounts("query1", 10);
      String result2 = autoCompleteService.getKeywordsWithCounts("query2", 10);
      String result3 = autoCompleteService.getKeywordsWithCounts("query3", 10);

      // Then - Each should return correct results
      assertThat(result1).contains("term1|o||1");
      assertThat(result2).contains("term2|o||2");
      assertThat(result3).contains("term3|o||3");

      verify(taxonomySearcher).searchAllDepths("query1", 10);
      verify(taxonomySearcher).searchAllDepths("query2", 10);
      verify(taxonomySearcher).searchAllDepths("query3", 10);
    }
  }

  @Nested
  @DisplayName("Edge cases and boundary conditions")
  class EdgeCasesAndBoundaryConditions {

    @Test
    @DisplayName("should handle limit of 1")
    void shouldHandleLimitOfOne() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("first", 1, null, false)
      );
      when(taxonomySearcher.searchAllDepths("test", 1)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("test", 1);

      // Then
      assertThat(result).isEqualTo("first|o||1\\n");
      verify(taxonomySearcher).searchAllDepths("test", 1);
    }

    @Test
    @DisplayName("should handle exactly MAX_LIMIT results")
    void shouldHandleExactlyMaxLimitResults() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = new java.util.ArrayList<>();
      for (int i = 0; i < 200; i++) {
        mockNodes.add(new TaxonomyNode("term" + i, i, null, false));
      }
      when(taxonomySearcher.searchAllDepths("test", 200)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("test", 200);

      // Then
      assertThat(result.split("\\\\n")).hasSize(200);
    }

    @Test
    @DisplayName("should handle EFO IDs with different formats")
    void shouldHandleEfoIdsWithDifferentFormats() throws IOException {
      // Given - Different EFO ID formats
      String[] efoIds = {
          "http://purl.obolibrary.org/obo/CL_0000000",
          "http://www.ebi.ac.uk/efo/EFO_0000001",
          "http://purl.obolibrary.org/obo/GO_0008150"
      };

      for (String efoId : efoIds) {
        List<TaxonomyNode> mockChildren = List.of(
            new TaxonomyNode("child", 1, null, false)
        );
        when(taxonomySearcher.getChildrenByEfoId(efoId, 20)).thenReturn(mockChildren);
      }

      // When/Then - Each format should work
      for (String efoId : efoIds) {
        String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);
        assertThat(result).isEqualTo("child|o||1\\n");
      }
    }

    @Test
    @DisplayName("should handle terms with unicode characters")
    void shouldHandleTermsWithUnicodeCharacters() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("α-cell", 5, null, false),
          new TaxonomyNode("β-cell", 3, null, false),
          new TaxonomyNode("γδ T cell", 2, null, false)
      );
      when(taxonomySearcher.searchAllDepths("α", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("α", 10);

      // Then
      assertThat(result).contains("α-cell");
      assertThat(result).contains("β-cell");
      assertThat(result).contains("γδ T cell");
    }

    @Test
    @DisplayName("should handle null efoId in TaxonomyNode")
    void shouldHandleNullEfoIdInNode() throws IOException {
      // Given
      List<TaxonomyNode> mockNodes = List.of(
          new TaxonomyNode("term", 1, null, false) // null efoId
      );
      when(taxonomySearcher.searchAllDepths("test", 10)).thenReturn(mockNodes);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("test", 10);

      // Then - Should not crash, should format with empty efoId
      assertThat(result).isEqualTo("term|o||1\\n");
    }
  }
}
