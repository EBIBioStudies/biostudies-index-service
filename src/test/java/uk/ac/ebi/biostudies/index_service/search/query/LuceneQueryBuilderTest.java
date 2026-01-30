package uk.ac.ebi.biostudies.index_service.search.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
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
import uk.ac.ebi.biostudies.index_service.analysis.AnalyzerManager;
import uk.ac.ebi.biostudies.index_service.config.SubCollectionConfig;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;

@ExtendWith(MockitoExtension.class)
@DisplayName("LuceneQueryBuilder Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class LuceneQueryBuilderTest {

  @Mock private AnalyzerManager analyzerManager;
  @Mock private IndexManager indexManager;
  @Mock private QueryExpander queryExpander;
  @Mock private CollectionRegistryService collectionRegistryService;
  @Mock private TaxonomyManager taxonomyManager;
  @Mock private LuceneIndexConfig indexConfig;
  @Mock private SubCollectionConfig subCollectionConfig;
  @Mock private FacetService facetService;

  private LuceneQueryBuilder queryBuilder;

  @BeforeEach
  void setUp() {
    // Default mock behaviors
    Analyzer defaultAnalyzer = new StandardAnalyzer();
    PerFieldAnalyzerWrapper perFieldAnalyzer =
        new PerFieldAnalyzerWrapper(
            defaultAnalyzer, new HashMap<>() // Empty field-specific analyzers
        );

    when(analyzerManager.getPerFieldAnalyzerWrapper()).thenReturn(perFieldAnalyzer);

    // LuceneQueryBuilder now uses indexConfig.getIndexedFieldsCache() for BioStudiesQueryParser
    when(indexConfig.getIndexedFieldsCache()).thenReturn(new String[] {"title", "content", "author"});

    // BioStudiesQueryParser consults the registry for field types (e.g. LONG range handling)
    CollectionRegistry registry = mock(CollectionRegistry.class);
    when(collectionRegistryService.getCurrentRegistry()).thenReturn(registry);
    when(registry.getGlobalPropertyRegistry()).thenReturn(new HashMap<>());

    // Prevent NPE if collection filter path is reached without explicit stubbing
    when(subCollectionConfig.getChildren(anyString())).thenReturn(List.of());

    queryBuilder =
        new LuceneQueryBuilder(
            analyzerManager,
            indexManager,
            queryExpander,
            collectionRegistryService,
            taxonomyManager,
            indexConfig,
            subCollectionConfig,
            facetService);
  }

  @Nested
  @DisplayName("Basic Query Building")
  class BasicQueryBuilding {

    @Test
    @DisplayName("Should build query with all pipeline steps applied")
    void shouldBuildQueryWithAllSteps() {
      // Arrange
      String queryString = "cancer";
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any(Query.class))).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery(queryString, null, null);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getQuery());
      assertNotNull(result.getExpandedEfoTerms());
      assertNotNull(result.getExpandedSynonyms());

      verify(queryExpander).expand(any(Query.class));
    }

    @Test
    @DisplayName("Should handle empty query string and use match-all")
    void shouldHandleEmptyQueryString() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("", null, null);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getQuery());
    }

    @Test
    @DisplayName("Should handle null query string and use match-all")
    void shouldHandleNullQueryString() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery(null, null, null);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw QueryBuildException on parse error")
    void shouldThrowExceptionOnParseError() {
      // Arrange - Create a fresh analyzer mock that throws
      AnalyzerManager failingAnalyzer = mock(AnalyzerManager.class);
      when(failingAnalyzer.getPerFieldAnalyzerWrapper())
          .thenThrow(new RuntimeException("Parse error"));

      // Ensure indexed fields are present so failure comes from analyzer/parser setup, not null fields[]
      when(indexConfig.getIndexedFieldsCache()).thenReturn(new String[] {"title", "content", "author"});

      // Create a new query builder with the failing analyzer
      LuceneQueryBuilder testBuilder =
          new LuceneQueryBuilder(
              failingAnalyzer,
              indexManager,
              queryExpander,
              collectionRegistryService,
              taxonomyManager,
              indexConfig,
              subCollectionConfig,
              facetService);

      // Act & Assert
      assertThrows(
          QueryBuildException.class, () -> testBuilder.buildQuery("invalid:query:", null, null));
    }
  }

  @Nested
  @DisplayName("Query Expansion")
  class QueryExpansion {

    @Test
    @DisplayName("Should expand query with EFO terms")
    void shouldExpandQueryWithEFOTerms() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      Set<String> efoTerms = Set.of("EFO_0001", "EFO_0002");
      Set<String> synonyms = Set.of("synonym1", "synonym2");

      QueryResult expansionResult = QueryResult.builder()
          .query(mockQuery)
          .expandedEfoTerms(efoTerms)
          .expandedSynonyms(synonyms)
          .build();

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", null, null);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.getExpandedEfoTerms().size());
      assertEquals(2, result.getExpandedSynonyms().size());
      assertTrue(result.getExpandedEfoTerms().contains("EFO_0001"));
      assertTrue(result.getExpandedSynonyms().contains("synonym1"));
    }

    @Test
    @DisplayName("Should handle null query expander gracefully")
    void shouldHandleNullQueryExpander() {
      // Arrange - Create builder with null expander
      LuceneQueryBuilder testBuilder =
          new LuceneQueryBuilder(
              analyzerManager,
              indexManager,
              null, // null expander
              collectionRegistryService,
              taxonomyManager,
              indexConfig,
              subCollectionConfig,
              facetService);

      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = testBuilder.buildQuery("cancer", null, null);

      // Assert
      assertNotNull(result);
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }

    @Test
    @DisplayName("Should handle expansion failure gracefully")
    void shouldHandleExpansionFailure() {
      // Arrange
      when(queryExpander.expand(any())).thenThrow(new RuntimeException("Expansion failed"));
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", null, null);

      // Assert - Should return query with no expansion
      assertNotNull(result);
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }
  }

  @Nested
  @DisplayName("Field Filters")
  class FieldFilters {

    @Test
    @DisplayName("Should apply field filters from Map")
    void shouldApplyFieldFiltersFromMap() {
      // Arrange
      Map<String, Object> fields = new HashMap<>();
      fields.put("author", "smith");
      fields.put("title", "cancer");

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, fields);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should skip empty field values")
    void shouldSkipEmptyFieldValues() {
      // Arrange
      Map<String, Object> fields = new HashMap<>();
      fields.put("author", "");
      fields.put("title", "cancer");

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, fields);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should skip null field values")
    void shouldSkipNullFieldValues() {
      // Arrange
      Map<String, Object> fields = new HashMap<>();
      fields.put("author", null);
      fields.put("title", "cancer");

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, fields);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should skip 'query' field in filters")
    void shouldSkipQueryField() {
      // Arrange
      Map<String, Object> fields = new HashMap<>();
      fields.put("query", "should-be-ignored");
      fields.put("author", "smith");

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, fields);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle null fields map")
    void shouldHandleNullFields() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, null);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle empty fields map")
    void shouldHandleEmptyFields() {
      // Arrange
      Map<String, Object> fields = new HashMap<>();

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, fields);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle non-string field values")
    void shouldHandleNonStringFieldValues() {
      // Arrange
      Map<String, Object> fields = new HashMap<>();
      fields.put("count", 42);
      fields.put("active", true);

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, fields);

      // Assert
      assertNotNull(result);
    }
  }

  @Nested
  @DisplayName("Type Filter")
  class TypeFilter {

    @Test
    @DisplayName("Should apply type filter when configured")
    void shouldApplyTypeFilterWhenConfigured() {
      // Arrange
      Query typeFilter = new TermQuery(new Term("type", "collection"));
      when(indexConfig.getTypeFilterQuery()).thenReturn(typeFilter);

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", null, null);

      // Assert
      assertNotNull(result);
      Query resultQuery = result.getQuery();
      assertTrue(resultQuery instanceof BooleanQuery);
      verify(indexConfig).getTypeFilterQuery();
    }

    @Test
    @DisplayName("Should skip type filter when not configured")
    void shouldSkipTypeFilterWhenNotConfigured() {
      // Arrange
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", null, null);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should skip type filter when type already in query string")
    void shouldSkipTypeFilterWhenAlreadyInQuery() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildQuery("type:study", null, null);

      // Assert
      assertNotNull(result);
      verify(indexConfig, never()).getTypeFilterQuery();
    }

    @Test
    @DisplayName("Should skip type filter when type in field filters")
    void shouldSkipTypeFilterWhenInFieldFilters() {
      // Arrange
      Map<String, Object> fields = new HashMap<>();
      fields.put("type", "study");

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", null, fields);

      // Assert
      assertNotNull(result);
      verify(indexConfig, never()).getTypeFilterQuery();
    }
  }

  @Nested
  @DisplayName("Collection Filter")
  class CollectionFilter {

    @Test
    @DisplayName("Should apply collection filter when specified")
    void shouldApplyCollectionFilterWhenSpecified() {
      // Arrange
      String collection = "bioimages";
      PropertyDescriptor facetDescriptor = mock(PropertyDescriptor.class);

      when(collectionRegistryService.getPropertyDescriptor(anyString()))
          .thenReturn(facetDescriptor);

      FacetsConfig facetsConfig = new FacetsConfig();
      when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);
      when(subCollectionConfig.getChildren(collection)).thenReturn(List.of());

      when(facetService.addFacetDrillDownFilters(any(), any(), any()))
          .thenAnswer(invocation -> new DrillDownQuery(facetsConfig));

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", collection, null);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getQuery());
      verify(facetService).addFacetDrillDownFilters(any(), any(), any());
    }

    @Test
    @DisplayName("Should include subcollections in filter")
    void shouldIncludeSubcollectionsInFilter() {
      // Arrange
      String collection = "bioimages";
      List<String> subcollections = List.of("JCB", "BioImages-EMPIAR");
      PropertyDescriptor facetDescriptor = mock(PropertyDescriptor.class);

      when(collectionRegistryService.getPropertyDescriptor(anyString()))
          .thenReturn(facetDescriptor);

      when(subCollectionConfig.getChildren(collection)).thenReturn(subcollections);

      FacetsConfig facetsConfig = new FacetsConfig();
      when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);

      when(facetService.addFacetDrillDownFilters(any(), any(), any()))
          .thenAnswer(invocation -> new DrillDownQuery(facetsConfig));

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", collection, null);

      // Assert
      assertNotNull(result);
      verify(subCollectionConfig).getChildren(collection);
      verify(facetService).addFacetDrillDownFilters(any(), any(), any());
    }

    @Test
    @DisplayName("Should skip collection filter for 'public' collection")
    void shouldSkipCollectionFilterForPublic() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", "public", null);

      // Assert
      assertNotNull(result);
      verify(facetService, never()).addFacetDrillDownFilters(any(), any(), any());
    }

    @Test
    @DisplayName("Should skip collection filter for null collection")
    void shouldSkipCollectionFilterForNull() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", null, null);

      // Assert
      assertNotNull(result);
      verify(facetService, never()).addFacetDrillDownFilters(any(), any(), any());
    }

    @Test
    @DisplayName("Should skip collection filter for empty collection")
    void shouldSkipCollectionFilterForEmpty() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", "", null);

      // Assert
      assertNotNull(result);
      verify(facetService, never()).addFacetDrillDownFilters(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle missing facet descriptor gracefully")
    void shouldHandleMissingFacetDescriptor() {
      // Arrange
      when(collectionRegistryService.getPropertyDescriptor(anyString())).thenReturn(null);

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", "BioImages", null);

      // Assert
      assertNotNull(result);
      verify(facetService, never()).addFacetDrillDownFilters(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle missing facets config gracefully")
    void shouldHandleMissingFacetsConfig() {
      // Arrange
      PropertyDescriptor facetDescriptor = mock(PropertyDescriptor.class);
      when(collectionRegistryService.getPropertyDescriptor(anyString()))
          .thenReturn(facetDescriptor);
      when(taxonomyManager.getFacetsConfig()).thenReturn(null);

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", "BioImages", null);

      // Assert
      assertNotNull(result);
      verify(facetService, never()).addFacetDrillDownFilters(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("Complete Query Pipeline")
  class CompleteQueryPipeline {

    @Test
    @DisplayName("Should apply all filters in correct order")
    void shouldApplyAllFiltersInCorrectOrder() {
      // Arrange
      String queryString = "cancer";
      String collection = "bioimages";
      Map<String, Object> fields = new HashMap<>();
      fields.put("author", "smith");

      Query typeFilter = new TermQuery(new Term("type", "collection"));
      PropertyDescriptor facetDescriptor = mock(PropertyDescriptor.class);

      when(indexConfig.getTypeFilterQuery()).thenReturn(typeFilter);
      when(collectionRegistryService.getPropertyDescriptor(anyString()))
          .thenReturn(facetDescriptor);

      when(subCollectionConfig.getChildren(collection)).thenReturn(List.of());

      FacetsConfig facetsConfig = new FacetsConfig();
      when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);

      when(facetService.addFacetDrillDownFilters(any(), any(), any()))
          .thenAnswer(invocation -> new DrillDownQuery(facetsConfig));

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildQuery(queryString, collection, fields);

      // Assert
      assertNotNull(result);

      // Verify order of operations
      var inOrder = inOrder(queryExpander, indexConfig, facetService);

      inOrder.verify(queryExpander).expand(any());
      inOrder.verify(indexConfig).getTypeFilterQuery();
      inOrder.verify(facetService).addFacetDrillDownFilters(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("Unsecured Query Building")
  class UnsecuredQueryBuilding {

    @Test
    @DisplayName("Should skip type and collection filters for unsecured queries")
    void shouldSkipFiltersForUnsecuredQueries() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildUnsecuredQuery("cancer", null);

      // Assert
      assertNotNull(result);
      verify(indexConfig, never()).getTypeFilterQuery();
      verify(facetService, never()).addFacetDrillDownFilters(any(), any(), any());
    }

    @Test
    @DisplayName("Should apply field filters for unsecured queries")
    void shouldApplyFieldFiltersForUnsecuredQueries() {
      // Arrange
      Map<String, Object> fields = new HashMap<>();
      fields.put("author", "smith");

      Query mockQuery = new MatchAllDocsQuery();
      QueryResult expansionResult = QueryResult.withoutExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildUnsecuredQuery("cancer", fields);

      // Assert
      assertNotNull(result);
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }
  }
}
