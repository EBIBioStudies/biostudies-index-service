package uk.ac.ebi.biostudies.index_service.autocomplete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearchHit;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearcher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoCompleteService")
class AutoCompleteServiceTest {

  @Mock private EFOSearcher efoSearcher;

  private AutoCompleteService service;

  @BeforeEach
  void setUp() {
    service = new AutoCompleteService(efoSearcher);
  }

  /**
   * Creates dummy EFO search hits for testing.
   * All hits have null child field (not expandable) and no alternative terms.
   */
  private List<EFOSearchHit> createDummyHits(int count) {
    List<EFOSearchHit> hits = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      hits.add(
          new EFOSearchHit(
              "http://efo/" + i,
              "efo_" + i,
              "term" + i,
              null, // child
              null, // altTerm
              null, // synonyms
              null  // efoTerms
          ));
    }
    return hits;
  }

  @Nested
  @DisplayName("getKeywords()")
  class GetKeywords {

    @Test
    @DisplayName("should return formatted ontology terms when filtering disabled")
    void shouldReturnFormattedTermsWithoutFiltering() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      List<EFOSearchHit> hits = new ArrayList<>();
      hits.add(new EFOSearchHit("http://efo/123", "efo_123", "leukemia", "hasChild", null, null, null));
      hits.add(new EFOSearchHit("http://efo/456", "efo_456", "gene", null, null, null, null));
      hits.addAll(createDummyHits(8)); // Total 10 to prevent alt search

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(hits);

      // When
      String result = service.getKeywords("leuk", 10);

      // Then
      assertThat(result).startsWith("leukemia|o|http://efo/123\n");
      assertThat(result).contains("gene|o|\n");
      assertThat(result).doesNotContain("|t|");
      verify(efoSearcher, never()).getTermFrequency(anyString());
    }

    @Test
    @DisplayName("should filter terms by index presence when enabled")
    void shouldFilterTermsByIndexPresence() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", true);

      List<EFOSearchHit> hits = new ArrayList<>();
      hits.add(new EFOSearchHit("http://efo/123", "efo_123", "leukemia", null, null, null, null));
      hits.add(new EFOSearchHit("http://efo/456", "efo_456", "nonexistent", null, null, null, null));
      hits.add(new EFOSearchHit("http://efo/789", "efo_789", "cancer", null, null, null, null));
      hits.addAll(createDummyHits(7)); // Total 10

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(hits);

      // Mock term frequencies
      when(efoSearcher.getTermFrequency("leukemia")).thenReturn(10);
      when(efoSearcher.getTermFrequency("nonexistent")).thenReturn(0);
      when(efoSearcher.getTermFrequency("cancer")).thenReturn(5);
      when(efoSearcher.getTermFrequency(startsWith("term"))).thenReturn(1);

      // When
      String result = service.getKeywords("leuk", 10);

      // Then
      assertThat(result)
          .contains("leukemia|o|")
          .contains("cancer|o|")
          .doesNotContain("nonexistent");

      verify(efoSearcher).getTermFrequency("leukemia");
      verify(efoSearcher).getTermFrequency("nonexistent");
      verify(efoSearcher).getTermFrequency("cancer");
    }

    @Test
    @DisplayName("should add alternative terms when primary results insufficient")
    void shouldAddAlternativeTermsWhenNeeded() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      List<EFOSearchHit> primaryHits = List.of(
          new EFOSearchHit("http://efo/123", "efo_123", "leukemia", null, null, null, null)
      );

      List<EFOSearchHit> altHits = List.of(
          new EFOSearchHit("http://efo/alt1", "efo_alt1", "leukemia", null, "leukaemia", null, null),
          new EFOSearchHit("http://efo/alt2", "efo_alt2", "leukemia", null, "blood cancer", null, null)
      );

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(primaryHits)
          .thenReturn(altHits);

      // When
      String result = service.getKeywords("leuk", 5);

      // Then
      assertThat(result).isEqualTo(
          "leukemia|o|\n" +
              "leukemia|t|content\n" +
              "leukemia|t|content\n"
      );

      // Verify alternative terms search was triggered
      verify(efoSearcher, times(2)).searchAll(any(Query.class), any(Sort.class), anyInt());
    }

    @Test
    @DisplayName("should not search alternative terms when primary results are sufficient")
    void shouldNotSearchAlternativeTermsWhenNotNeeded() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(createDummyHits(10));

      // When
      String result = service.getKeywords("leuk", 3);

      // Then
      assertThat(result.split("\n")).hasSize(3);

      // Verify only one search (primary terms only)
      verify(efoSearcher, times(1)).searchAll(any(Query.class), any(Sort.class), anyInt());
    }

    @Test
    @DisplayName("should cap limit at MAX_LIMIT (200)")
    void shouldCapLimitAtMaximum() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      // Return enough results to prevent alternative term search
      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(createDummyHits(200));

      // When
      service.getKeywords("test", 999);

      // Then
      // Verify fetch limit is capped at 200 (not 999 * 3)
      verify(efoSearcher, times(1))
          .searchAll(any(Query.class), any(Sort.class), eq(200));
    }


    @Test
    @DisplayName("should add wildcard to simple queries")
    void shouldAddWildcardToSimpleQueries() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(createDummyHits(10));

      // When
      service.getKeywords("leuk", 10);

      // Then
      verify(efoSearcher, times(1))
          .searchAll(
              argThat(query -> query.toString().contains("leuk*")),
              any(Sort.class),
              anyInt());
    }

    @Test
    @DisplayName("should not modify queries with quotes")
    void shouldNotModifyQueriesWithQuotes() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(createDummyHits(10));

      // When
      service.getKeywords("\"exact phrase\"", 10);

      // Then
      verify(efoSearcher, times(1))
          .searchAll(
              argThat(query -> !query.toString().contains("*")),
              any(Sort.class),
              anyInt());
    }

    @Test
    @DisplayName("should not modify queries with boolean operators")
    void shouldNotModifyQueriesWithBooleanOperators() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(createDummyHits(10));

      // When
      service.getKeywords("leuk AND cancer", 10);

      // Then
      verify(efoSearcher, times(1))
          .searchAll(
              argThat(query -> !query.toString().contains("AND*")),
              any(Sort.class),
              anyInt());
    }

    @Test
    @DisplayName("should not modify queries with existing wildcards")
    void shouldNotModifyQueriesWithWildcards() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(createDummyHits(10));

      // When
      service.getKeywords("leuk*", 10);

      // Then
      verify(efoSearcher, times(1))
          .searchAll(
              argThat(query -> !query.toString().contains("leuk**")),
              any(Sort.class),
              anyInt());
    }

    @Test
    @DisplayName("should handle query parsing errors gracefully")
    void shouldHandleQueryParsingErrors() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenThrow(new RuntimeException("Query parsing failed"));

      // When
      String result = service.getKeywords("test", 10);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should fallback to unfiltered results when term frequency check fails")
    void shouldFallbackOnFilteringError() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", true);

      List<EFOSearchHit> hits = List.of(
          new EFOSearchHit("http://efo/123", "efo_123", "leukemia", null, null, null, null),
          new EFOSearchHit("http://efo/456", "efo_456", "cancer", null, null, null, null)
      );

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(hits);
      when(efoSearcher.getTermFrequency(anyString()))
          .thenThrow(new IOException("Read error"));

      // When
      String result = service.getKeywords("leuk", 10);

      // Then
      assertThat(result).isNotEmpty(); // Should fallback to unfiltered
      assertThat(result.split("\n")).hasSize(2);
    }

    @Test
    @DisplayName("should filter alternative terms correctly")
    void shouldFilterAlternativeTerms() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", true);

      List<EFOSearchHit> primaryHits = List.of(
          new EFOSearchHit("http://efo/123", "efo_123", "leukemia", null, null, null, null)
      );

      List<EFOSearchHit> altHits = List.of(
          new EFOSearchHit("http://efo/alt1", "efo_alt1", null, null, "leukaemia", null, null),
          new EFOSearchHit("http://efo/alt2", "efo_alt2", null, null, "nonexistent term", null, null)
      );

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(primaryHits)
          .thenReturn(altHits);

      when(efoSearcher.getTermFrequency("leukemia")).thenReturn(10);
      when(efoSearcher.getTermFrequency("leukaemia")).thenReturn(5);
      when(efoSearcher.getTermFrequency("nonexistent term")).thenReturn(0);

      // When
      String result = service.getKeywords("leuk", 5);

      // Then
      assertThat(result).contains("leukemia|o|");
      verify(efoSearcher).getTermFrequency("leukemia");
      verify(efoSearcher).getTermFrequency("leukaemia");
      verify(efoSearcher).getTermFrequency("nonexistent term");
    }

    @Test
    @DisplayName("should stop filtering once limit is reached")
    void shouldStopFilteringOnceLimitReached() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", true);

      List<EFOSearchHit> hits = List.of(
          new EFOSearchHit("http://efo/1", "efo_1", "term1", null, null, null, null),
          new EFOSearchHit("http://efo/2", "efo_2", "term2", null, null, null, null),
          new EFOSearchHit("http://efo/3", "efo_3", "term3", null, null, null, null),
          new EFOSearchHit("http://efo/4", "efo_4", "term4", null, null, null, null)
      );

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(hits);

      when(efoSearcher.getTermFrequency(anyString())).thenReturn(10);

      // When
      service.getKeywords("test", 2);

      // Then
      // Should only check first 2 terms since limit is reached
      verify(efoSearcher).getTermFrequency("term1");
      verify(efoSearcher).getTermFrequency("term2");
      verify(efoSearcher, never()).getTermFrequency("term3");
      verify(efoSearcher, never()).getTermFrequency("term4");
    }
  }

  @Nested
  @DisplayName("getEfoTree()")
  class GetEfoTree {

    @Test
    @DisplayName("should return all children without filtering")
    void shouldReturnAllChildrenWithoutFiltering() throws Exception {
      // Given
      String parentId = "http://efo/parent123";

      List<EFOSearchHit> children = List.of(
          new EFOSearchHit("http://efo/child1", "efo_child1", "acute leukemia", "hasChild", null, null, null),
          new EFOSearchHit("http://efo/child2", "efo_child2", "chronic leukemia", null, null, null, null),
          new EFOSearchHit("http://efo/child3", "efo_child3", "aleukemic leukemia", null, null, null, null)
      );

      when(efoSearcher.searchAll(any(TermQuery.class), any(Sort.class), eq(500)))
          .thenReturn(children);

      // When
      String result = service.getEfoTree(parentId);

      // Then
      assertThat(result).isEqualTo(
          "acute leukemia|o|http://efo/child1\n" +
              "chronic leukemia|o|\n" +
              "aleukemic leukemia|o|\n"
      );

      // Verify no filtering was attempted
      verify(efoSearcher, never()).getTermFrequency(anyString());
    }

    @Test
    @DisplayName("should query with lowercase parent ID")
    void shouldQueryWithLowercaseParentId() throws Exception {
      // Given
      String parentId = "HTTP://EFO/PARENT123";

      when(efoSearcher.searchAll(any(TermQuery.class), any(Sort.class), anyInt()))
          .thenReturn(List.of());

      // When
      service.getEfoTree(parentId);

      // Then
      verify(efoSearcher).searchAll(
          argThat(query -> {
            TermQuery tq = (TermQuery) query;
            return tq.getTerm().text().equals("http://efo/parent123");
          }),
          any(Sort.class),
          anyInt()
      );
    }

    @Test
    @DisplayName("should sort children alphabetically")
    void shouldSortChildrenAlphabetically() throws Exception {
      // Given
      String parentId = "http://efo/parent123";

      when(efoSearcher.searchAll(any(TermQuery.class), any(Sort.class), anyInt()))
          .thenReturn(List.of());

      // When
      service.getEfoTree(parentId);

      // Then
      verify(efoSearcher).searchAll(
          any(TermQuery.class),
          argThat(sort -> {
            SortField[] fields = sort.getSort();
            return fields.length == 1
                && fields[0].getField().equals(EFOField.TERM.getFieldName())
                && fields[0].getType() == SortField.Type.STRING
                && !fields[0].getReverse(); // ascending
          }),
          anyInt()
      );
    }

    @Test
    @DisplayName("should use MAX_TREE_LIMIT of 500")
    void shouldUseTreeLimit() throws Exception {
      // Given
      String parentId = "http://efo/parent123";

      when(efoSearcher.searchAll(any(TermQuery.class), any(Sort.class), anyInt()))
          .thenReturn(List.of());

      // When
      service.getEfoTree(parentId);

      // Then
      verify(efoSearcher).searchAll(any(TermQuery.class), any(Sort.class), eq(500));
    }

    @Test
    @DisplayName("should handle nodes with no children")
    void shouldHandleNoChildren() throws Exception {
      // Given
      String parentId = "http://efo/leafnode";

      when(efoSearcher.searchAll(any(TermQuery.class), any(Sort.class), anyInt()))
          .thenReturn(List.of());

      // When
      String result = service.getEfoTree(parentId);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should include URI only for expandable children")
    void shouldIncludeUriOnlyForExpandableChildren() throws Exception {
      // Given
      String parentId = "http://efo/parent123";

      List<EFOSearchHit> children = List.of(
          new EFOSearchHit("http://efo/1", "efo_1", "has children", "child1", null, null, null),
          new EFOSearchHit("http://efo/2", "efo_2", "leaf node", null, null, null, null)
      );

      when(efoSearcher.searchAll(any(TermQuery.class), any(Sort.class), anyInt()))
          .thenReturn(children);

      // When
      String result = service.getEfoTree(parentId);

      // Then
      assertThat(result).isEqualTo(
          "has children|o|http://efo/1\n" +
              "leaf node|o|\n"
      );
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("should handle empty search results")
    void shouldHandleEmptySearchResults() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(List.of());

      // When
      String result = service.getKeywords("nonexistent", 10);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should handle null child field gracefully")
    void shouldHandleNullChildField() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", false);

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(createDummyHits(10));

      // When
      String result = service.getKeywords("test", 10);

      // Then
      // All dummy hits have null child field, so none should have URI after |o|
      String[] lines = result.split("\n");
      assertThat(lines).hasSize(10);
      for (String line : lines) {
        assertThat(line).matches("term\\d+\\|o\\|"); // No URI after |o|
      }
    }

    @Test
    @DisplayName("should skip hits with null term when filtering")
    void shouldSkipNullTermsInFiltering() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", true);

      List<EFOSearchHit> hits = List.of(
          new EFOSearchHit("http://efo/1", "efo_1", null, null, null, null, null),
          new EFOSearchHit("http://efo/2", "efo_2", "valid", null, null, null, null)
      );

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(hits);

      when(efoSearcher.getTermFrequency("valid")).thenReturn(1);

      // When
      String result = service.getKeywords("test", 10);

      // Then
      assertThat(result).contains("valid|o|");
      assertThat(result.split("\n")).hasSize(1);
      verify(efoSearcher, never()).getTermFrequency(isNull());
    }

    @Test
    @DisplayName("should skip alternative terms with null altTerm when filtering")
    void shouldSkipNullAltTermsInFiltering() throws Exception {
      // Given
      ReflectionTestUtils.setField(service, "filterByIndex", true);

      List<EFOSearchHit> primaryHits = List.of(
          new EFOSearchHit("http://efo/1", "efo_1", "leukemia", null, null, null, null)
      );

      List<EFOSearchHit> altHits = List.of(
          new EFOSearchHit("http://efo/alt1", "efo_alt1", null, null, null, null, null),
          new EFOSearchHit("http://efo/alt2", "efo_alt2", null, null, "valid alt", null, null)
      );

      when(efoSearcher.searchAll(any(Query.class), any(Sort.class), anyInt()))
          .thenReturn(primaryHits)
          .thenReturn(altHits);

      when(efoSearcher.getTermFrequency("leukemia")).thenReturn(10);
      when(efoSearcher.getTermFrequency("valid alt")).thenReturn(5);

      // When
      String result = service.getKeywords("leuk", 5);

      // Then
      assertThat(result).contains("leukemia|o|");
      verify(efoSearcher).getTermFrequency("leukemia");
      verify(efoSearcher).getTermFrequency("valid alt");
      verify(efoSearcher, times(2)).getTermFrequency(anyString()); // Only 2 valid terms checked
    }
  }
}
