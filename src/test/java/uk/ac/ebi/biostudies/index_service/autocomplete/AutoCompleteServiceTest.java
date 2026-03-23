package uk.ac.ebi.biostudies.index_service.autocomplete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearchHit;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearcher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoCompleteService")
class AutoCompleteServiceTest {

  @Mock private EFOHierarchyService efoHierarchyService;
  @Mock private EFOTermCountService efoTermCountService;
  @Mock private EFOTermMatcher efoTermMatcher;
  @Mock private EFOSearcher efoSearcher;

  @MockitoBean
  @Value("${autocomplete.filter-by-index:true}")
  private boolean filterByIndex = true;

  private AutoCompleteService autoCompleteService;

  @BeforeEach
  void setUp() {
    autoCompleteService =
        new AutoCompleteService(
            efoSearcher, efoHierarchyService, efoTermCountService, efoTermMatcher);
  }

  @Nested
  @DisplayName("getKeywordsWithCounts()")
  class GetKeywordsWithCounts {

    @Test
    @DisplayName("should return formatted results for valid query")
    void shouldReturnFormattedResultsForValidQuery() throws IOException {
      // Given
      List<EFOSearchHit> searchHits =
          List.of(
              new EFOSearchHit("1", "EFO:0001", "mouse", null, null, List.of(), List.of()),
              new EFOSearchHit("2", "EFO:0002", "macrophage", null, null, List.of(), List.of()));
      when(efoSearcher.searchAll(any(Query.class), any(), anyInt())).thenReturn(searchHits);
      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:0001")).thenReturn(5L);
      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:0002")).thenReturn(3L);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("mouse", 10);

      // Then
      assertThat(result).isEqualTo("mouse|o||5\nmacrophage|o||3\n");
      verify(efoSearcher).searchAll(any(Query.class), any(), anyInt());
      verify(efoTermCountService).countIncludingDescendantsByEfoId("EFO:0001");
      verify(efoTermCountService).countIncludingDescendantsByEfoId("EFO:0002");
      // No verification of efoHierarchyService here; expand is now count-aware.
    }

    @Test
    @DisplayName("should return empty string when no results found")
    void shouldReturnEmptyStringWhenNoResults() throws IOException {
      // Given
      when(efoSearcher.searchAll(any(Query.class), any(), anyInt())).thenReturn(List.of());

      // When
      String result = autoCompleteService.getKeywordsWithCounts("xyz", 10);

      // Then
      assertThat(result).isEmpty();
      verify(efoSearcher).searchAll(any(Query.class), any(), anyInt());
      verifyNoInteractions(efoTermCountService, efoHierarchyService, efoTermMatcher);
    }

    @Test
    @DisplayName("should return empty string for null query")
    void shouldReturnEmptyStringForNullQuery() {
      assertThat(autoCompleteService.getKeywordsWithCounts(null, 10)).isEmpty();
      verifyNoInteractions(efoSearcher, efoTermCountService, efoHierarchyService, efoTermMatcher);
    }

    @Test
    @DisplayName("should return empty string for empty query")
    void shouldReturnEmptyStringForEmptyQuery() {
      assertThat(autoCompleteService.getKeywordsWithCounts("", 10)).isEmpty();
      verifyNoInteractions(efoSearcher, efoTermCountService, efoHierarchyService, efoTermMatcher);
    }

    @Test
    @DisplayName("should return empty string for whitespace-only query")
    void shouldReturnEmptyStringForWhitespaceOnlyQuery() {
      assertThat(autoCompleteService.getKeywordsWithCounts("   ", 10)).isEmpty();
      verifyNoInteractions(efoSearcher, efoTermCountService, efoHierarchyService, efoTermMatcher);
    }

    @Test
    @DisplayName("should cap limit to MAX_LIMIT when exceeded")
    void shouldCapLimitToMaxWhenExceeded() throws IOException {
      // Given
      when(efoSearcher.searchAll(any(Query.class), any(), anyInt())).thenReturn(List.of());

      // When
      autoCompleteService.getKeywordsWithCounts("test", 999);

      // Then
      verify(efoSearcher).searchAll(any(Query.class), any(), eq(200));
    }

    @Test
    @DisplayName("should use computed fetchLimit when below max")
    void shouldUseComputedFetchLimitWhenBelowMax() throws IOException {
      // Given
      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), eq(15))) // 5*3=15
          .thenReturn(List.of());

      // When
      autoCompleteService.getKeywordsWithCounts("test", 5);

      // Then
      verify(efoSearcher).searchAll(any(Query.class), any(Sort.class), eq(15));
    }

    @Test
    @DisplayName("should return empty string when search throws RuntimeException")
    void shouldReturnEmptyStringOnSearchRuntimeException() {
      doThrow(new RuntimeException("Search error"))
          .when(efoSearcher)
          .searchAll(any(Query.class), any(Sort.class), anyInt());

      String result = autoCompleteService.getKeywordsWithCounts("test", 10);

      assertThat(result).isEmpty();
      verify(efoSearcher).searchAll(any(Query.class), any(Sort.class), anyInt());
    }

    @Test
    @DisplayName("should handle mixed expandable and non-expandable results")
    void shouldHandleMixedExpandableResults() throws IOException {
      // Given
      List<EFOSearchHit> searchHits =
          List.of(
              new EFOSearchHit("1", "EFO:0001", "cell", null, null, List.of(), List.of()),
              new EFOSearchHit(
                  "2", "EFO:0002", "cellular process", null, null, List.of(), List.of()));
      when(efoSearcher.searchAll(any(Query.class), any(), anyInt())).thenReturn(searchHits);
      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:0001")).thenReturn(10L);
      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:0002")).thenReturn(5L);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("cell", 10);

      // Then
      assertThat(result).isEqualTo("cell|o||10\ncellular process|o||5\n");
    }

    @Test
    @DisplayName("should handle zero count results")
    void shouldHandleZeroCountResults() throws IOException {
      // Given
      List<EFOSearchHit> searchHits =
          List.of(new EFOSearchHit("1", "EFO:0001", "rare term", null, null, List.of(), List.of()));
      when(efoSearcher.searchAll(any(Query.class), any(), anyInt())).thenReturn(searchHits);
      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:0001")).thenReturn(0L);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("rare", 10);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should resolve missing efoID from term matcher")
    void shouldResolveMissingEfoIdFromTermMatcher() throws IOException {
      // Given
      List<EFOSearchHit> searchHits =
          List.of(
              new EFOSearchHit(
                  "http://purl.obolibrary.org/obo/cl_0000588",
                  null,
                  "odontoclast",
                  null,
                  null,
                  List.of(),
                  List.of()));

      when(efoSearcher.searchAll(any(Query.class), any(), anyInt())).thenReturn(searchHits);
      when(efoTermCountService.countIncludingDescendantsByEfoId(
              "http://purl.obolibrary.org/obo/cl_0000588"))
          .thenReturn(7L);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("odontoclast", 10);

      // Then
      assertThat(result).isEqualTo("odontoclast|o||7\n");
      verifyNoInteractions(efoTermMatcher);
    }

    @Test
    @DisplayName("should resolve efo id from term matcher when ids are missing")
    void shouldResolveEfoIdFromTermMatcherWhenIdsMissing() throws IOException {
      // Given
      List<EFOSearchHit> searchHits =
          List.of(new EFOSearchHit(null, null, "odontoclast", null, null, List.of(), List.of()));

      when(efoSearcher.searchAll(any(Query.class), any(), anyInt())).thenReturn(searchHits);
      when(efoTermMatcher.getEFOId("odontoclast"))
          .thenReturn("http://purl.obolibrary.org/obo/cl_0000588");
      when(efoTermCountService.countIncludingDescendantsByEfoId(
              "http://purl.obolibrary.org/obo/cl_0000588"))
          .thenReturn(7L);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("odontoclast", 10);

      // Then
      assertThat(result).isEqualTo("odontoclast|o||7\n");
      verify(efoTermMatcher).getEFOId("odontoclast");
    }

    @Test
    @DisplayName("should skip hits when efo id cannot be resolved")
    void shouldSkipHitsWhenEfoIdCannotBeResolved() throws IOException {
      // Given
      List<EFOSearchHit> searchHits =
          List.of(new EFOSearchHit(null, null, "unknownterm", null, null, List.of(), List.of()));
      when(efoSearcher.searchAll(any(Query.class), any(), anyInt())).thenReturn(searchHits);
      when(efoTermMatcher.getEFOId("unknownterm")).thenReturn(null);

      // When
      String result = autoCompleteService.getKeywordsWithCounts("unknown", 10);

      // Then
      assertThat(result).isEmpty();
      verify(efoTermMatcher).getEFOId("unknownterm");
      verifyNoInteractions(efoTermCountService, efoHierarchyService);
    }
  }

  @Nested
  @DisplayName("getEfoTreeWithCounts()")
  class GetEfoTreeWithCounts {

    @Test
    @DisplayName("should return children for valid EFO ID")
    void shouldReturnChildrenForValidEfoId() throws IOException {
      // Given
      String efoId = "EFO:0000738";
      when(efoHierarchyService.getChildrenByEfoId(efoId))
          .thenReturn(List.of("osteoclast", "macrophage"));

      when(efoTermMatcher.getEFOId("osteoclast")).thenReturn("EFO:child1");
      when(efoTermMatcher.getEFOId("macrophage")).thenReturn("EFO:child2");

      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:child1")).thenReturn(5L);
      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:child2")).thenReturn(3L);
      when(efoHierarchyService.hasChildrenByEfoId("EFO:child1")).thenReturn(true);
      when(efoHierarchyService.hasChildrenByEfoId("EFO:child2")).thenReturn(false);

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEqualTo("osteoclast|o|EFO:child1|5\nmacrophage|o||3\n");
      verify(efoHierarchyService).getChildrenByEfoId(efoId);
      verify(efoTermMatcher).getEFOId("osteoclast");
      verify(efoTermMatcher).getEFOId("macrophage");
      verify(efoTermCountService).countIncludingDescendantsByEfoId("EFO:child1");
      verify(efoTermCountService).countIncludingDescendantsByEfoId("EFO:child2");
    }

    @Test
    @DisplayName("should return empty string for leaf node")
    void shouldReturnEmptyStringForLeafNode() {
      // Given
      String efoId = "EFO:leaf";
      when(efoHierarchyService.getChildrenByEfoId(efoId)).thenReturn(List.of());

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEmpty();
      verify(efoHierarchyService).getChildrenByEfoId(efoId);
      verifyNoInteractions(efoSearcher, efoTermCountService, efoTermMatcher);
    }

    @Test
    @DisplayName("should return empty string for null EFO ID")
    void shouldReturnEmptyStringForNullEfoId() {
      assertThat(autoCompleteService.getEfoTreeWithCounts(null, 20)).isEmpty();
      verifyNoInteractions(efoHierarchyService, efoSearcher, efoTermCountService, efoTermMatcher);
    }

    @Test
    @DisplayName("should return empty string for empty EFO ID")
    void shouldReturnEmptyStringForEmptyEfoId() {
      assertThat(autoCompleteService.getEfoTreeWithCounts("", 20)).isEmpty();
      verifyNoInteractions(efoHierarchyService, efoSearcher, efoTermCountService, efoTermMatcher);
    }

    @Test
    @DisplayName("should return empty string for whitespace-only EFO ID")
    void shouldReturnEmptyStringForWhitespaceOnlyEfoId() {
      assertThat(autoCompleteService.getEfoTreeWithCounts("   ", 20)).isEmpty();
      verifyNoInteractions(efoHierarchyService, efoSearcher, efoTermCountService, efoTermMatcher);
    }

    @Test
    @DisplayName("should use default limit when limit is zero")
    void shouldUseDefaultLimitWhenZero() throws IOException {
      // Given
      String efoId = "EFO:0000000";
      when(efoHierarchyService.getChildrenByEfoId(efoId)).thenReturn(List.of("child"));
      when(efoTermMatcher.getEFOId("child")).thenReturn("EFO:child");
      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:child")).thenReturn(1L);
      when(efoHierarchyService.hasChildrenByEfoId("EFO:child")).thenReturn(false);

      // When
      autoCompleteService.getEfoTreeWithCounts(efoId, 0);

      // Then
      verify(efoHierarchyService).getChildrenByEfoId(efoId);
      verify(efoTermMatcher).getEFOId("child");
    }

    @Test
    @DisplayName("should use default limit when limit is negative")
    void shouldUseDefaultLimitWhenNegative() throws IOException {
      // Given
      String efoId = "EFO:0000000";
      when(efoHierarchyService.getChildrenByEfoId(efoId)).thenReturn(List.of("child"));
      when(efoTermMatcher.getEFOId("child")).thenReturn("EFO:child");
      when(efoTermCountService.countIncludingDescendantsByEfoId("EFO:child")).thenReturn(1L);
      when(efoHierarchyService.hasChildrenByEfoId("EFO:child")).thenReturn(false);

      // When
      autoCompleteService.getEfoTreeWithCounts(efoId, -5);

      // Then
      verify(efoHierarchyService).getChildrenByEfoId(efoId);
    }

    @Test
    @DisplayName("should return empty string when IOException occurs")
    void shouldReturnEmptyStringOnIOException() throws IOException {
      // Given
      String efoId = "EFO:0000000";
      when(efoHierarchyService.getChildrenByEfoId(efoId))
          .thenThrow(new RuntimeException("Index error"));

      // When
      String result = autoCompleteService.getEfoTreeWithCounts(efoId, 20);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("getKeywords()")
  class GetKeywords {

    @Test
    @DisplayName("should return formatted ontology and alternative terms")
    void shouldReturnFormattedOntologyAndAlternativeTerms() throws IOException {
      // Force filtering
      ReflectionTestUtils.setField(autoCompleteService, "filterByIndex", true);

      // Ontology hit (TERM field)
      EFOSearchHit termHit =
          new EFOSearchHit("1", "EFO:0001", "cell type", null, null, List.of(), List.of());

      // Alternative hit (ALTERNATIVE_TERMS field)
      EFOSearchHit altHit =
          new EFOSearchHit("2", null, "cellular", null, "cellular", List.of(), List.of());

      // Stubbing order: 1st TERM search → 2nd ALTERNATIVE_TERMS search
      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(List.of(termHit)) // TERM: 1 hit < limit*FETCH → alternatives
          .thenReturn(List.of(altHit)); // ALTERNATIVE_TERMS

      // Filtering calls (filterByIndex=true)
      when(efoSearcher.getTermFrequency("cell type")).thenReturn(2); // termHit.term()
      when(efoSearcher.getTermFrequency("cellular")).thenReturn(1); // altHit.altTerm()

      // When
      String result = autoCompleteService.getKeywords("cell", 10);

      // Then
      assertThat(result.trim()).isEqualTo("cell type|o|\ncellular|t|content");
      verify(efoSearcher, times(2)).searchAll(any(Query.class), any(Sort.class), anyInt());
      verify(efoSearcher).getTermFrequency("cell type");
      verify(efoSearcher).getTermFrequency("cellular");
    }

    @Test
    @DisplayName("should return empty string for null query")
    void shouldReturnEmptyStringForNullQuery() {
      assertThat(autoCompleteService.getKeywords(null, 10)).isEmpty();
      verifyNoInteractions(efoSearcher, efoTermCountService, efoHierarchyService, efoTermMatcher);
    }
  }
}
