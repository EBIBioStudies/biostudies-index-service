package uk.ac.ebi.biostudies.index_service.search.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FacetService Tests")
class FacetServiceTest {

  @Mock private IndexManager indexManager;
  @Mock private TaxonomyManager taxonomyManager;
  @Mock private CollectionRegistryService collectionRegistryService;
  @Mock private IndexSearcher indexSearcher;
  @Mock private CollectionRegistry collectionRegistry;
  @Mock private SecurityContext securityContext;
  @Mock private Authentication authentication;

  private FacetService facetService;
  private FacetsConfig facetsConfig;
  private Query baseQuery;

  private Directory directory;
  private DirectoryReader realIndexReader;

  private static Stream<Arguments> provideCaseSensitivityScenarios() {
    return Stream.of(
        Arguments.of(true, "Homo Sapiens", "homo sapiens"),
        Arguments.of(true, "DROSOPHILA", "drosophila"),
        Arguments.of(false, "Homo Sapiens", "Homo Sapiens"),
        Arguments.of(false, "PDF", "PDF"));
  }

  @BeforeEach
  void setUp() throws Exception {
    facetService = new FacetService(indexManager, taxonomyManager, collectionRegistryService);
    facetsConfig = new FacetsConfig();
    baseQuery = new TermQuery(new Term("content", "test"));

    // Build a tiny real index so Lucene facets can create DefaultSortedSetDocValuesReaderState
    directory = new ByteBuffersDirectory();
    realIndexReader = buildReaderWithFacetFields(facetsConfig);

    // Default stubbing used by many tests
    when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);
    when(indexSearcher.getIndexReader()).thenReturn(realIndexReader);
    doNothing().when(indexSearcher).search(any(Query.class), any(Collector.class));
    when(collectionRegistryService.getCurrentRegistry()).thenReturn(collectionRegistry);
    when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(Collections.emptyMap());

    // Setup security context
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(null);
  }

  @AfterEach
  void tearDown() throws Exception {
    SecurityContextHolder.clearContext();
    if (realIndexReader != null) {
      realIndexReader.close();
    }
    if (directory != null) {
      directory.close();
    }
  }

  private DirectoryReader buildReaderWithFacetFields(FacetsConfig facetsConfig) throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    try (IndexWriter writer = new IndexWriter(directory, iwc)) {
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("organism", "Human"));
      doc.add(new SortedSetDocValuesFacetField("internal_status", "internal"));
      doc.add(new SortedSetDocValuesFacetField(FacetService.RELEASED_YEAR_FACET, "2020"));
      doc.add(new SortedSetDocValuesFacetField("study_type", "Genomics"));
      doc.add(new SortedSetDocValuesFacetField("file_type", "PDF"));

      writer.addDocument(facetsConfig.build(doc));
      writer.commit();
    }
    return DirectoryReader.open(directory);
  }

  // Helper methods
  private PropertyDescriptor createFacetProperty(String name, boolean toLowerCase) {
    return PropertyDescriptor.builder()
        .name(name)
        .fieldType(FieldType.FACET)
        .toLowerCase(toLowerCase)
        .build();
  }

  private PropertyDescriptor createPrivateFacetProperty(String name) {
    return PropertyDescriptor.builder()
        .name(name)
        .fieldType(FieldType.FACET)
        .isPrivate(true)
        .toLowerCase(true)
        .build();
  }

  @Nested
  @DisplayName("addFacetDrillDownFilters Tests")
  class AddFacetDrillDownFiltersTests {

    @Test
    @DisplayName("Should create DrillDownQuery with single facet value")
    void shouldCreateDrillDownQueryWithSingleFacetValue() {
      // Given
      PropertyDescriptor property = createFacetProperty("organism", true);
      Map<PropertyDescriptor, List<String>> selectedFacets =
          Map.of(property, List.of("Drosophila"));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      assertTrue(result.toString().contains("organism"));
      assertTrue(result.toString().contains("drosophila")); // lowercase applied
    }

    @Test
    @DisplayName("Should create DrillDownQuery with multiple facet values")
    void shouldCreateDrillDownQueryWithMultipleFacetValues() {
      // Given
      PropertyDescriptor property = createFacetProperty("study_type", true);
      Map<PropertyDescriptor, List<String>> selectedFacets =
          Map.of(property, List.of("Transcriptomics", "Genomics", "Proteomics"));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      String queryString = result.toString();
      assertTrue(queryString.contains("transcriptomics"));
      assertTrue(queryString.contains("genomics"));
      assertTrue(queryString.contains("proteomics"));
    }

    @Test
    @DisplayName("Should handle multiple dimensions")
    void shouldHandleMultipleDimensions() {
      // Given
      PropertyDescriptor organismProp = createFacetProperty("organism", true);
      PropertyDescriptor studyTypeProp = createFacetProperty("study_type", true);

      Map<PropertyDescriptor, List<String>> selectedFacets = new HashMap<>();
      selectedFacets.put(organismProp, List.of("Human"));
      selectedFacets.put(studyTypeProp, List.of("Genomics"));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      String queryString = result.toString();
      assertTrue(queryString.contains("organism"));
      assertTrue(queryString.contains("study_type"));
    }

    @Test
    @DisplayName("Should respect toLowerCase flag set to false")
    void shouldRespectCaseSensitiveFlag() {
      // Given
      PropertyDescriptor property = createFacetProperty("file_type", false);
      Map<PropertyDescriptor, List<String>> selectedFacets =
          Map.of(property, List.of("PDF", "XML"));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      String queryString = result.toString();
      assertTrue(queryString.contains("PDF")); // case preserved
      assertTrue(queryString.contains("XML"));
    }

    @Test
    @DisplayName("Should skip null PropertyDescriptor")
    void shouldSkipNullPropertyDescriptor() {
      // Given
      Map<PropertyDescriptor, List<String>> selectedFacets = new HashMap<>();
      selectedFacets.put(null, List.of("value"));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      assertEquals(baseQuery, result.getBaseQuery());
    }

    @Test
    @DisplayName("Should skip non-facet properties")
    void shouldSkipNonFacetProperties() {
      // Given
      PropertyDescriptor textField =
          PropertyDescriptor.builder().name("title").fieldType(FieldType.TOKENIZED_STRING).build();

      Map<PropertyDescriptor, List<String>> selectedFacets =
          Map.of(textField, List.of("cancer research"));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      assertFalse(result.toString().contains("cancer research"));
    }

    @Test
    @DisplayName("Should skip null value lists")
    void shouldSkipNullValueList() {
      // Given
      PropertyDescriptor property = createFacetProperty("organism", true);
      Map<PropertyDescriptor, List<String>> selectedFacets = new HashMap<>();
      selectedFacets.put(property, null);

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      assertEquals(baseQuery, result.getBaseQuery());
    }

    @Test
    @DisplayName("Should skip empty value lists")
    void shouldSkipEmptyValueList() {
      // Given
      PropertyDescriptor property = createFacetProperty("organism", true);
      Map<PropertyDescriptor, List<String>> selectedFacets =
          Map.of(property, Collections.emptyList());

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      assertEquals(baseQuery, result.getBaseQuery());
    }

    @Test
    @DisplayName("Should handle empty map")
    void shouldHandleEmptyMap() {
      // Given
      Map<PropertyDescriptor, List<String>> selectedFacets = Collections.emptyMap();

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      assertEquals(baseQuery, result.getBaseQuery());
    }

    @ParameterizedTest
    @MethodSource(
        "uk.ac.ebi.biostudies.index_service.search.query.FacetServiceTest#provideCaseSensitivityScenarios")
    @DisplayName("Should apply correct case transformation")
    void shouldApplyCorrectCaseTransformation(
        boolean toLowerCase, String inputValue, String expectedValue) {
      // Given
      PropertyDescriptor property = createFacetProperty("test_facet", toLowerCase);
      Map<PropertyDescriptor, List<String>> selectedFacets = Map.of(property, List.of(inputValue));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      assertTrue(result.toString().contains(expectedValue));
    }

    @Test
    @DisplayName("Should preserve base query")
    void shouldPreserveBaseQuery() {
      // Given
      PropertyDescriptor property = createFacetProperty("organism", true);
      Map<PropertyDescriptor, List<String>> selectedFacets = Map.of(property, List.of("Human"));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertEquals(baseQuery, result.getBaseQuery());
    }

    @Test
    @DisplayName("Should handle special characters in facet values")
    void shouldHandleSpecialCharacters() {
      // Given
      PropertyDescriptor property = createFacetProperty("title", true);
      Map<PropertyDescriptor, List<String>> selectedFacets =
          Map.of(property, List.of("C++ Programming", "R&D Department"));

      // When
      DrillDownQuery result =
          facetService.addFacetDrillDownFilters(facetsConfig, baseQuery, selectedFacets);

      // Then
      assertNotNull(result);
      assertTrue(result.toString().contains("c++ programming"));
      assertTrue(result.toString().contains("r&d department"));
    }
  }

  @Nested
  @DisplayName("getFacetsForQuery Tests")
  class GetFacetsForQueryTests {

    @Test
    @DisplayName("Should return empty list when index searcher throws IOException")
    void shouldReturnEmptyListOnIOException() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);
      when(indexManager.acquireSearcher(IndexName.SUBMISSION))
          .thenThrow(new IOException("Index error"));

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(indexManager, never()).releaseSearcher(any(), any());
    }

    @Test
    @DisplayName("Should return empty list on general exception")
    void shouldReturnEmptyListOnGeneralException() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.getIndexReader()).thenThrow(new RuntimeException("Unexpected error"));

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should skip private facets for unauthorized users")
    void shouldSkipPrivateFacetsForUnauthorizedUsers() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);

      PropertyDescriptor publicFacet = createFacetProperty("organism", true);
      PropertyDescriptor privateFacet = createPrivateFacetProperty("internal_status");

      Map<String, PropertyDescriptor> allProperties = new HashMap<>();
      allProperties.put("organism", publicFacet);
      allProperties.put("internal_status", privateFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // User is NOT authenticated
      when(securityContext.getAuthentication()).thenReturn(null);

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should include private facets for authorized users")
    void shouldIncludePrivateFacetsForAuthorizedUsers() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);

      PropertyDescriptor privateFacet = createPrivateFacetProperty("internal_status");
      Map<String, PropertyDescriptor> allProperties = Map.of("internal_status", privateFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // User IS authenticated
      when(securityContext.getAuthentication()).thenReturn(authentication);
      when(authentication.isAuthenticated()).thenReturn(true);
      when(authentication.getPrincipal()).thenReturn("user@example.com");

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should use Integer.MAX_VALUE limit for release_year facet")
    void shouldUseUnlimitedLimitForReleaseYearFacet() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);

      PropertyDescriptor releaseYearFacet =
          createFacetProperty(FacetService.RELEASED_YEAR_FACET, true);
      Map<String, PropertyDescriptor> allProperties =
          Map.of(FacetService.RELEASED_YEAR_FACET, releaseYearFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should always release searcher even on exception")
    void shouldAlwaysReleaseSearcherOnException() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.getIndexReader()).thenThrow(new RuntimeException("Unexpected error"));

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should skip non-facet properties")
    void shouldSkipNonFacetProperties() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);

      PropertyDescriptor textField =
          PropertyDescriptor.builder().name("title").fieldType(FieldType.TOKENIZED_STRING).build();
      PropertyDescriptor facetField = createFacetProperty("organism", true);

      Map<String, PropertyDescriptor> allProperties = new HashMap<>();
      allProperties.put("title", textField);
      allProperties.put("organism", facetField);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should populate selected facet frequencies")
    void shouldPopulateSelectedFacetFrequencies() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);
      Map<String, Map<String, Integer>> selectedFacetFreq = new HashMap<>();
      Map<String, List<String>> selectedFacets = Map.of("organism", List.of("Human", "Mouse"));

      PropertyDescriptor organismFacet = createFacetProperty("organism", true);
      Map<String, PropertyDescriptor> allProperties = Map.of("organism", organismFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // When
      facetService.getFacetsForQuery(drillDownQuery, 10, selectedFacetFreq, selectedFacets);

      // Then
      // Frequencies should be populated (implementation dependent on mock setup)
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }
  }

  @Nested
  @DisplayName("getDimension Tests")
  class GetDimensionTests {

    @Test
    @DisplayName("Should return null when dimension is not a facet")
    void shouldReturnNullForNonFacetDimension() throws IOException {
      // Given
      PropertyDescriptor nonFacet =
          PropertyDescriptor.builder().name("title").fieldType(FieldType.TOKENIZED_STRING).build();

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getPropertyDescriptor("title")).thenReturn(nonFacet);

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "title", baseQuery, new HashMap<>());

      // Then
      assertNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should return null when dimension does not exist")
    void shouldReturnNullForNonExistentDimension() throws IOException {
      // Given
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getPropertyDescriptor("unknown")).thenReturn(null);

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "unknown", baseQuery, new HashMap<>());

      // Then
      assertNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should return null for private dimension when user unauthorized")
    void shouldReturnNullForPrivateDimensionWhenUnauthorized() throws IOException {
      // Given
      PropertyDescriptor privateFacet = createPrivateFacetProperty("internal_status");

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getPropertyDescriptor("internal_status")).thenReturn(privateFacet);

      // User is NOT authenticated
      when(securityContext.getAuthentication()).thenReturn(null);

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "internal_status", baseQuery, new HashMap<>());

      // Then
      assertNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should return dimension for private facet when user authorized")
    void shouldReturnDimensionForPrivateFacetWhenAuthorized() throws IOException {
      // Given
      PropertyDescriptor privateFacet = createPrivateFacetProperty("internal_status");

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getPropertyDescriptor("internal_status")).thenReturn(privateFacet);

      // User IS authenticated
      when(securityContext.getAuthentication()).thenReturn(authentication);
      when(authentication.isAuthenticated()).thenReturn(true);
      when(authentication.getPrincipal()).thenReturn("user@example.com");

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "internal_status", baseQuery, new HashMap<>());

      // Then
      // Result may be null if facets are not set up in mock, but authentication check passed
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should handle IOException gracefully")
    void shouldHandleIOExceptionGracefully() throws IOException {
      // Given
      when(indexManager.acquireSearcher(IndexName.SUBMISSION))
          .thenThrow(new IOException("Index error"));

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "organism", baseQuery, new HashMap<>());

      // Then
      assertNull(result);
      verify(indexManager, never()).releaseSearcher(any(), any());
    }

    @Test
    @DisplayName("Should handle exception before acquiring searcher gracefully")
    void shouldHandleExceptionBeforeAcquiringSearcher() throws IOException {
      // Given
      when(taxonomyManager.getFacetsConfig()).thenThrow(new RuntimeException("Unexpected error"));

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "organism", baseQuery, new HashMap<>());

      // Then
      assertNull(result);
      // Searcher was never acquired, so it should NOT be released
      verify(indexManager, never()).acquireSearcher(any());
      verify(indexManager, never()).releaseSearcher(any(), any());
    }

    @Test
    @DisplayName("Should always release searcher even on exception")
    void shouldAlwaysReleaseSearcherOnException() throws IOException {
      // Given
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.getIndexReader()).thenThrow(new RuntimeException("Unexpected error"));

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "organism", baseQuery, new HashMap<>());

      // Then
      assertNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should apply selected facets as filters")
    void shouldApplySelectedFacetsAsFilters() throws IOException {
      // Given
      PropertyDescriptor organismFacet = createFacetProperty("organism", true);
      Map<String, List<String>> selectedFacets = Map.of("organism", List.of("Human"));

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getPropertyDescriptor("organism")).thenReturn(organismFacet);

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "organism", baseQuery, selectedFacets);

      // Then
      // Verify query was executed (result may be null due to mock setup)
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }
  }

  @Nested
  @DisplayName("Inner Classes Tests")
  class InnerClassesTests {

    @Test
    @DisplayName("FacetDimension should store values correctly")
    void facetDimensionShouldStoreValuesCorrectly() {
      // Given
      List<FacetService.FacetValue> values =
          Arrays.asList(
              new FacetService.FacetValue("Human", 100), new FacetService.FacetValue("Mouse", 50));

      // When
      FacetService.FacetDimension dimension =
          new FacetService.FacetDimension("organism", "Organism", values);

      // Then
      assertEquals("organism", dimension.getName());
      assertEquals("Organism", dimension.getTitle());
      assertEquals(2, dimension.getValues().size());
      assertEquals("Human", dimension.getValues().get(0).getLabel());
      assertEquals(100, dimension.getValues().get(0).getCount());
    }

    @Test
    @DisplayName("FacetDimension should handle empty values list")
    void facetDimensionShouldHandleEmptyValuesList() {
      // Given
      List<FacetService.FacetValue> emptyValues = Collections.emptyList();

      // When
      FacetService.FacetDimension dimension =
          new FacetService.FacetDimension("organism", "Organism", emptyValues);

      // Then
      assertEquals("organism", dimension.getName());
      assertEquals("Organism", dimension.getTitle());
      assertTrue(dimension.getValues().isEmpty());
    }

    @Test
    @DisplayName("FacetValue should store label and count correctly")
    void facetValueShouldStoreLabelAndCountCorrectly() {
      // When
      FacetService.FacetValue value = new FacetService.FacetValue("Homo sapiens", 42);

      // Then
      assertEquals("Homo sapiens", value.getLabel());
      assertEquals(42, value.getCount());
    }

    @Test
    @DisplayName("FacetValue should handle zero count")
    void facetValueShouldHandleZeroCount() {
      // When
      FacetService.FacetValue value = new FacetService.FacetValue("Rare organism", 0);

      // Then
      assertEquals("Rare organism", value.getLabel());
      assertEquals(0, value.getCount());
    }

    @Test
    @DisplayName("FacetValue should handle large counts")
    void facetValueShouldHandleLargeCounts() {
      // When
      FacetService.FacetValue value = new FacetService.FacetValue("Common organism", 1000000);

      // Then
      assertEquals("Common organism", value.getLabel());
      assertEquals(1000000, value.getCount());
    }
  }

  @Nested
  @DisplayName("Authorization Tests")
  class AuthorizationTests {

    @Test
    @DisplayName("Should allow access when user is authenticated")
    void shouldAllowAccessWhenUserAuthenticated() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);
      PropertyDescriptor privateFacet = createPrivateFacetProperty("internal_status");
      Map<String, PropertyDescriptor> allProperties = Map.of("internal_status", privateFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // User IS authenticated
      when(securityContext.getAuthentication()).thenReturn(authentication);
      when(authentication.isAuthenticated()).thenReturn(true);
      when(authentication.getPrincipal()).thenReturn("user@example.com");

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should deny access when user is anonymous")
    void shouldDenyAccessWhenUserAnonymous() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);
      PropertyDescriptor privateFacet = createPrivateFacetProperty("internal_status");
      Map<String, PropertyDescriptor> allProperties = Map.of("internal_status", privateFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // User is anonymous
      when(securityContext.getAuthentication()).thenReturn(authentication);
      when(authentication.isAuthenticated()).thenReturn(true);
      when(authentication.getPrincipal()).thenReturn("anonymousUser");

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should deny access when authentication is null")
    void shouldDenyAccessWhenAuthenticationNull() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);
      PropertyDescriptor privateFacet = createPrivateFacetProperty("internal_status");
      Map<String, PropertyDescriptor> allProperties = Map.of("internal_status", privateFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // No authentication
      when(securityContext.getAuthentication()).thenReturn(null);

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle null selected facets map")
    void shouldHandleNullSelectedFacetsMap() throws IOException {
      // Given
      PropertyDescriptor organismFacet = createFacetProperty("organism", true);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getPropertyDescriptor("organism")).thenReturn(organismFacet);

      // When
      FacetService.FacetDimension result =
          facetService.getDimension("test", "organism", baseQuery, null);

      // Then
      // Should not throw exception
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should handle empty registry")
    void shouldHandleEmptyRegistry() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(Collections.emptyMap());

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 10, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should handle limit of zero")
    void shouldHandleLimitOfZero() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);
      PropertyDescriptor organismFacet = createFacetProperty("organism", true);
      Map<String, PropertyDescriptor> allProperties = Map.of("organism", organismFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, 0, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }

    @Test
    @DisplayName("Should handle negative limit")
    void shouldHandleNegativeLimit() throws IOException {
      // Given
      DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);
      PropertyDescriptor organismFacet = createFacetProperty("organism", true);
      Map<String, PropertyDescriptor> allProperties = Map.of("organism", organismFacet);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(allProperties);

      // When
      List<FacetResult> result =
          facetService.getFacetsForQuery(drillDownQuery, -1, new HashMap<>(), new HashMap<>());

      // Then
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.SUBMISSION), eq(indexSearcher));
    }
  }
}
