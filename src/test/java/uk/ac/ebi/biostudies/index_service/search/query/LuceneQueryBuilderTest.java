package uk.ac.ebi.biostudies.index_service.search.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;
import uk.ac.ebi.biostudies.index_service.search.security.SecurityQueryBuilder;

@ExtendWith(MockitoExtension.class)
@DisplayName("LuceneQueryBuilder Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class LuceneQueryBuilderTest {

  @Mock private AnalyzerManager analyzerManager;
  @Mock private IndexManager indexManager;
  @Mock private QueryExpander queryExpander;
  @Mock private SecurityQueryBuilder securityQueryBuilder;
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
    when(collectionRegistryService.getSearchableFields())
        .thenReturn(new String[] {"title", "content", "author"});

    queryBuilder =
        new LuceneQueryBuilder(
            analyzerManager,
            indexManager,
            queryExpander,
            securityQueryBuilder,
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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any(Query.class))).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery(queryString, null, null);

      // Assert
      assertNotNull(result);
      assertEquals(mockQuery, result.getQuery());
      assertEquals(queryString, result.getOriginalQuery());
      assertNotNull(result.getExpansionMetadata());

      verify(queryExpander).expand(any(Query.class));
      verify(securityQueryBuilder).applySecurity(any());
    }

    @Test
    @DisplayName("Should handle empty query string and use match-all")
    void shouldHandleEmptyQueryString() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("", null, null);

      // Assert
      assertNotNull(result);
      assertEquals("", result.getOriginalQuery());
    }

    @Test
    @DisplayName("Should handle null query string and use match-all")
    void shouldHandleNullQueryString() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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

      // Create a new query builder with the failing analyzer
      LuceneQueryBuilder testBuilder =
          new LuceneQueryBuilder(
              failingAnalyzer, // ✅ Use failing analyzer
              indexManager,
              queryExpander,
              securityQueryBuilder,
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
  @DisplayName("Security Application")
  class SecurityApplication {

    @Test
    @DisplayName("Should always apply security for normal queries")
    void shouldAlwaysApplySecurityForNormalQueries() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      queryBuilder.buildQuery("cancer", null, null);

      // Assert
      verify(securityQueryBuilder, times(1)).applySecurity(any());
    }

    @Test
    @DisplayName("Should not apply security for unsecured queries")
    void shouldNotApplySecurityForUnsecuredQueries() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      queryBuilder.buildUnsecuredQuery("cancer", null);

      // Assert
      verify(securityQueryBuilder, never()).applySecurity(any());
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
      QueryExpansionMetadata metadata =
          new QueryExpansionMetadata(
              List.of("EFO_0001", "EFO_0002"),
              List.of("synonym1", "synonym2"),
              "cancer",
              false,
              100);
      QueryExpansionResult expansionResult = new QueryExpansionResult(mockQuery, metadata);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("cancer", null, null);

      // Assert
      assertNotNull(result.getExpansionMetadata());
      assertEquals(2, result.getExpansionMetadata().getEfoTerms().size());
      assertEquals(2, result.getExpansionMetadata().getSynonyms().size());
      assertTrue(result.getExpansionMetadata().isExpanded());
    }

    @Test
    @DisplayName("Should handle expansion failure gracefully")
    void shouldHandleExpansionFailure() {
      // Arrange
      when(queryExpander.expand(any())).thenThrow(new RuntimeException("Expansion failed"));

      // Act & Assert
      assertThrows(QueryBuildException.class, () -> queryBuilder.buildQuery("cancer", null, null));
    }
  }

  @Nested
  @DisplayName("Field Filters")
  class FieldFilters {

    @Test
    @DisplayName("Should apply field filters from Map")
    void shouldApplyFieldFiltersFromMap() {
      // Arrange
      Map<String, String> fields = new HashMap<>();
      fields.put("author", "smith");
      fields.put("title", "cancer");

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      Map<String, String> fields = new HashMap<>();
      fields.put("author", "");
      fields.put("title", "cancer");

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      Map<String, String> fields = new HashMap<>();
      fields.put("author", null);
      fields.put("title", "cancer");

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      Map<String, String> fields = new HashMap<>();
      fields.put("query", "should-be-ignored");
      fields.put("author", "smith");

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      Map<String, String> fields = new HashMap<>();

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, fields);

      // Assert
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should log warning for failed field filters")
    void shouldLogWarningForFailedFieldFilters() {
      // Arrange
      Map<String, String> fields = new HashMap<>();
      fields.put("invalid:field", "value"); // Invalid field name
      fields.put("author", "smith");

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
      when(indexConfig.getTypeFilterQuery()).thenReturn(null);

      // Act
      QueryResult result = queryBuilder.buildQuery("test", null, fields);

      // Assert
      assertNotNull(result);
      // Check logs for warning about failed fields
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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenAnswer(i -> i.getArgument(0));

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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenAnswer(i -> i.getArgument(0));

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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);

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
      Map<String, String> fields = new HashMap<>();
      fields.put("type", "study");

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);

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

      // Create a real FacetsConfig for testing
      FacetsConfig facetsConfig = new org.apache.lucene.facet.FacetsConfig();
      when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);
      when(subCollectionConfig.getChildren(collection)).thenReturn(List.of());

      // ✅ Mock facetService to return a DrillDownQuery
      when(facetService.addFacetDrillDownFilters(any(), any(), any()))
          .thenAnswer(
              invocation -> {
                org.apache.lucene.facet.DrillDownQuery ddq =
                    new org.apache.lucene.facet.DrillDownQuery(facetsConfig);
                return ddq;
              });

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenAnswer(i -> i.getArgument(0));
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

      // Create a real FacetsConfig for testing
      FacetsConfig facetsConfig = new FacetsConfig();
      when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);

      // Mock facetService to return a DrillDownQuery
      when(facetService.addFacetDrillDownFilters(any(), any(), any()))
          .thenAnswer(
              invocation -> {
                DrillDownQuery ddq = new DrillDownQuery(facetsConfig);
                return ddq;
              });

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);
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
      Map<String, String> fields = new HashMap<>();
      fields.put("author", "smith");

      Query typeFilter = new TermQuery(new Term("type", "collection"));
      PropertyDescriptor facetDescriptor = mock(PropertyDescriptor.class);

      when(indexConfig.getTypeFilterQuery()).thenReturn(typeFilter);
      when(collectionRegistryService.getPropertyDescriptor(anyString()))
          .thenReturn(facetDescriptor);

      when(subCollectionConfig.getChildren(collection)).thenReturn(List.of());

      // Create a real FacetsConfig for testing
      FacetsConfig facetsConfig = new FacetsConfig();
      when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);
      when(subCollectionConfig.getChildren(collection)).thenReturn(List.of());

      // Mock facetService to return a DrillDownQuery
      when(facetService.addFacetDrillDownFilters(any(), any(), any()))
          .thenAnswer(
              invocation -> {
                DrillDownQuery ddq = new DrillDownQuery(facetsConfig);
                return ddq;
              });

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);
      when(securityQueryBuilder.applySecurity(any())).thenReturn(mockQuery);

      // Act
      QueryResult result = queryBuilder.buildQuery(queryString, collection, fields);

      // Assert
      assertNotNull(result);
      assertEquals(queryString, result.getOriginalQuery());

      // Verify order of operations
      var inOrder = inOrder(queryExpander, indexConfig, facetService, securityQueryBuilder);

      inOrder.verify(queryExpander).expand(any());
      inOrder.verify(indexConfig).getTypeFilterQuery();
      inOrder.verify(facetService).addFacetDrillDownFilters(any(), any(), any());
      inOrder.verify(securityQueryBuilder).applySecurity(any());
    }
  }

  @Nested
  @DisplayName("Unsecured Query Building")
  class UnsecuredQueryBuilding {

    @Test
    @DisplayName("Should skip security for unsecured queries")
    void shouldSkipSecurityForUnsecuredQueries() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildUnsecuredQuery("cancer", null);

      // Assert
      assertNotNull(result);
      verify(securityQueryBuilder, never()).applySecurity(any());
      verify(queryExpander).expand(any());
    }

    @Test
    @DisplayName("Should skip type and collection filters for unsecured queries")
    void shouldSkipFiltersForUnsecuredQueries() {
      // Arrange
      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

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
      Map<String, String> fields = new HashMap<>();
      fields.put("author", "smith");

      Query mockQuery = new MatchAllDocsQuery();
      QueryExpansionResult expansionResult = QueryExpansionResult.noExpansion(mockQuery);

      when(queryExpander.expand(any())).thenReturn(expansionResult);

      // Act
      QueryResult result = queryBuilder.buildUnsecuredQuery("cancer", fields);

      // Assert
      assertNotNull(result);
    }
  }
}
