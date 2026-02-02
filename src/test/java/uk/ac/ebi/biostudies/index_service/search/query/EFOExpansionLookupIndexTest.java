package uk.ac.ebi.biostudies.index_service.search.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.exceptions.SearchException;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearchHit;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearcher;

/** Tests for {@link EFOExpansionLookupIndex}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("EFOExpansionLookupIndex Tests")
class EFOExpansionLookupIndexTest {

  @Mock private EFOSearcher efoSearcher;

  private EFOExpansionLookupIndex lookupIndex;

  @BeforeEach
  void setUp() {
    lookupIndex = new EFOExpansionLookupIndex(efoSearcher);
  }

  @Nested
  @DisplayName("Constructor Validation")
  class ConstructorValidation {

    @Test
    @DisplayName("Should throw NullPointerException when efoSearcher is null")
    void shouldThrowExceptionWhenSearcherIsNull() {
      assertThrows(NullPointerException.class, () -> new EFOExpansionLookupIndex(null));
    }
  }

  @Nested
  @DisplayName("Term Query Expansion")
  class TermQueryExpansion {

    @Test
    @DisplayName("Should expand term query with synonyms and EFO terms")
    void shouldExpandTermQueryWithSynonymsAndEfo() {
      // Arrange
      Query query = new TermQuery(new Term("content", "cancer"));

      EFOSearchHit hit =
          new EFOSearchHit(
              "EFO:0000311",
              "cancer",
              null,
              null,
              List.of("neoplasm", "tumor"),
              List.of("EFO:0000311", "EFO:0000616"));

      when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(List.of(hit));

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.synonyms.size());
      assertTrue(result.synonyms.contains("neoplasm"));
      assertTrue(result.synonyms.contains("tumor"));
      assertEquals(2, result.efo.size());
      assertTrue(result.efo.contains("EFO:0000311"));
      assertTrue(result.efo.contains("EFO:0000616"));

      verify(efoSearcher).searchAll(any(TermQuery.class), eq(16));
    }

    @Test
    @DisplayName("Should return empty expansion when no matches found")
    void shouldReturnEmptyExpansionWhenNoMatches() {
      // Arrange
      Query query = new TermQuery(new Term("content", "unknownterm"));

      when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(Collections.emptyList());

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());

      verify(efoSearcher).searchAll(any(TermQuery.class), eq(16));
    }

    @Test
    @DisplayName("Should expand term query with only synonyms")
    void shouldExpandWithOnlySynonyms() {
      // Arrange
      Query query = new TermQuery(new Term("title", "disease"));

      EFOSearchHit hit =
          new EFOSearchHit(
              "EFO:0000408",
              "disease",
              null,
              null,
              List.of("illness", "disorder"),
              Collections.emptyList());

      when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(List.of(hit));

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.synonyms.size());
      assertTrue(result.synonyms.contains("illness"));
      assertTrue(result.synonyms.contains("disorder"));
      assertTrue(result.efo.isEmpty());
    }

    @Test
    @DisplayName("Should expand term query with only EFO terms")
    void shouldExpandWithOnlyEfoTerms() {
      // Arrange
      Query query = new TermQuery(new Term("content", "heart"));

      EFOSearchHit hit =
          new EFOSearchHit(
              "EFO:0000815", "heart", null, null, Collections.emptyList(), List.of("EFO:0000815"));

      when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(List.of(hit));

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertEquals(1, result.efo.size());
      assertTrue(result.efo.contains("EFO:0000815"));
    }

    @Test
    @DisplayName("Should filter out empty and whitespace-only synonyms")
    void shouldFilterOutEmptySynonyms() {
      // Arrange
      Query query = new TermQuery(new Term("content", "test"));

      // Use Arrays.asList() which allows nulls, or new ArrayList<>()
      List<String> synonymsWithNulls = new ArrayList<>();
      synonymsWithNulls.add("valid");
      synonymsWithNulls.add("");
      synonymsWithNulls.add("  ");
      synonymsWithNulls.add(null);
      synonymsWithNulls.add("another");

      List<String> efoTermsWithNulls = new ArrayList<>();
      efoTermsWithNulls.add("EFO:0000001");
      efoTermsWithNulls.add("");
      efoTermsWithNulls.add(null);

      EFOSearchHit hit =
          new EFOSearchHit("EFO:0000001", "test", null, null, synonymsWithNulls, efoTermsWithNulls);

      when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(List.of(hit));

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.synonyms.size());
      assertTrue(result.synonyms.contains("valid"));
      assertTrue(result.synonyms.contains("another"));
      assertEquals(1, result.efo.size());
      assertTrue(result.efo.contains("EFO:0000001"));
    }

    @Nested
    @DisplayName("Phrase Query Expansion")
    class PhraseQueryExpansion {

      @Test
      @DisplayName("Should expand phrase query by converting to term query")
      void shouldExpandPhraseQuery() {
        // Arrange
        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        builder.add(new Term("content", "breast"));
        builder.add(new Term("content", "cancer"));
        Query query = builder.build();

        EFOSearchHit hit =
            new EFOSearchHit(
                "EFO:0000305",
                "breast cancer",
                null,
                null,
                List.of("mammary carcinoma"),
                List.of("EFO:0000305"));

        when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(List.of(hit));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.synonyms.size());
        assertTrue(result.synonyms.contains("mammary carcinoma"));
        assertEquals(1, result.efo.size());
        assertTrue(result.efo.contains("EFO:0000305"));
      }

      @Test
      @DisplayName("Should handle empty phrase query gracefully")
      void shouldHandleEmptyPhraseQuery() {
        // Arrange
        PhraseQuery emptyPhraseQuery = new PhraseQuery.Builder().build();

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(emptyPhraseQuery);

        // Assert
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());

        // Should not search if query conversion fails
        verify(efoSearcher, never()).searchAll(any(Query.class), anyInt());
      }
    }

    @Nested
    @DisplayName("Wildcard and Prefix Query Expansion")
    class WildcardAndPrefixExpansion {

      @Test
      @DisplayName("Should expand prefix query")
      void shouldExpandPrefixQuery() {
        // Arrange
        Query query = new PrefixQuery(new Term("content", "diab"));

        EFOSearchHit hit =
            new EFOSearchHit(
                "EFO:0000400",
                "diabetes",
                null,
                null,
                List.of("diabetes mellitus"),
                List.of("EFO:0000400"));

        when(efoSearcher.searchAll(any(PrefixQuery.class), eq(16))).thenReturn(List.of(hit));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.synonyms.size());
        assertTrue(result.synonyms.contains("diabetes mellitus"));
        assertEquals(1, result.efo.size());
        assertTrue(result.efo.contains("EFO:0000400"));
      }

      @Test
      @DisplayName("Should expand wildcard query")
      void shouldExpandWildcardQuery() {
        // Arrange
        Query query = new WildcardQuery(new Term("content", "neur*"));

        EFOSearchHit hit =
            new EFOSearchHit(
                "EFO:0000618",
                "neuron",
                null,
                null,
                List.of("nerve cell"),
                Collections.emptyList());

        when(efoSearcher.searchAll(any(WildcardQuery.class), eq(16))).thenReturn(List.of(hit));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.synonyms.size());
        assertTrue(result.synonyms.contains("nerve cell"));
      }
    }

    @Nested
    @DisplayName("Fuzzy Query Expansion")
    class FuzzyQueryExpansion {

      @Test
      @DisplayName("Should expand fuzzy query")
      void shouldExpandFuzzyQuery() {
        // Arrange
        Query query = new FuzzyQuery(new Term("content", "cancr"));

        EFOSearchHit hit =
            new EFOSearchHit(
                "EFO:0000311", "cancer", null, null, List.of("neoplasm"), List.of("EFO:0000311"));

        when(efoSearcher.searchAll(any(FuzzyQuery.class), eq(16))).thenReturn(List.of(hit));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.synonyms.size());
        assertTrue(result.synonyms.contains("neoplasm"));
      }
    }

    @Nested
    @DisplayName("Range Query Expansion")
    class RangeQueryExpansion {

      @Test
      @DisplayName("Should expand term range query")
      void shouldExpandTermRangeQuery() {
        // Arrange
        Query query = TermRangeQuery.newStringRange("content", "a", "d", true, true);

        EFOSearchHit hit =
            new EFOSearchHit(
                "EFO:0000001",
                "abnormality",
                null,
                null,
                List.of("anomaly"),
                Collections.emptyList());

        when(efoSearcher.searchAll(any(TermRangeQuery.class), eq(16))).thenReturn(List.of(hit));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.synonyms.size());
        assertTrue(result.synonyms.contains("anomaly"));
      }
    }

    @Nested
    @DisplayName("Multiple Documents")
    class MultipleDocuments {

      @Test
      @DisplayName("Should aggregate expansion terms from multiple hits")
      void shouldAggregateFromMultipleHits() {
        // Arrange
        Query query = new TermQuery(new Term("content", "brain"));

        EFOSearchHit hit1 =
            new EFOSearchHit(
                "EFO:0000302",
                "brain",
                "cerebrum",
                null,
                List.of("cerebrum"),
                List.of("EFO:0000302"));

        EFOSearchHit hit2 =
            new EFOSearchHit(
                "EFO:0000908",
                "brain",
                "encephalon",
                null,
                List.of("encephalon"),
                List.of("EFO:0000908"));

        when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(List.of(hit1, hit2));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.synonyms.size());
        assertTrue(result.synonyms.contains("cerebrum"));
        assertTrue(result.synonyms.contains("encephalon"));
        assertEquals(2, result.efo.size());
        assertTrue(result.efo.contains("EFO:0000302"));
        assertTrue(result.efo.contains("EFO:0000908"));
      }

      @Test
      @DisplayName("Should handle hits with null or empty collections")
      void shouldHandleHitsWithNullCollections() {
        // Arrange
        Query query = new TermQuery(new Term("content", "test"));

        EFOSearchHit hit1 = new EFOSearchHit("EFO:0000001", "test", null, null, null, null);
        EFOSearchHit hit2 =
            new EFOSearchHit(
                "EFO:0000002",
                "test",
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList());
        EFOSearchHit hit3 =
            new EFOSearchHit(
                "EFO:0000003", "test", null, null, List.of("valid"), List.of("EFO:0000003"));

        when(efoSearcher.searchAll(any(TermQuery.class), eq(16)))
            .thenReturn(List.of(hit1, hit2, hit3));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.synonyms.size());
        assertTrue(result.synonyms.contains("valid"));
        assertEquals(1, result.efo.size());
        assertTrue(result.efo.contains("EFO:0000003"));
      }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

      @Test
      @DisplayName("Should return empty expansion on SearchException")
      void shouldReturnEmptyExpansionOnSearchException() {
        // Arrange
        Query query = new TermQuery(new Term("content", "test"));

        when(efoSearcher.searchAll(any(Query.class), anyInt()))
            .thenThrow(new SearchException("Search failed"));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());
      }

      @Test
      @DisplayName("Should return empty expansion on unexpected exception")
      void shouldReturnEmptyExpansionOnUnexpectedException() {
        // Arrange
        Query query = new TermQuery(new Term("content", "test"));

        when(efoSearcher.searchAll(any(Query.class), anyInt()))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());
      }

      @Test
      @DisplayName("Should handle unsupported query type")
      void shouldHandleUnsupportedQueryType() {
        // Arrange
        Query unsupportedQuery = new MatchAllDocsQuery();

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(unsupportedQuery);

        // Assert
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());

        // Should not search for unsupported query types
        verify(efoSearcher, never()).searchAll(any(Query.class), anyInt());
      }

      @Test
      @DisplayName("Should handle exception during term extraction")
      void shouldHandleExceptionDuringExtraction() {
        // Arrange
        Query query = new TermQuery(new Term("content", "test"));

        // Create a mock that throws when methods are called
        EFOSearchHit badHit = mock(EFOSearchHit.class);
        when(badHit.synonyms()).thenThrow(new RuntimeException("Extraction failed"));

        when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(List.of(badHit));

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());
      }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

      @Test
      @DisplayName("Should handle null query gracefully")
      void shouldHandleNullQuery() {
        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());

        // Should not attempt to search
        verify(efoSearcher, never()).searchAll(any(), anyInt());
      }

      @Test
      @DisplayName("Should handle BooleanQuery as unsupported")
      void shouldHandleBooleanQueryAsUnsupported() {
        // Arrange
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term("field", "term1")), BooleanClause.Occur.MUST);
        builder.add(new TermQuery(new Term("field", "term2")), BooleanClause.Occur.SHOULD);
        Query booleanQuery = builder.build();

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(booleanQuery);

        // Assert
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());

        verify(efoSearcher, never()).searchAll(any(), anyInt());
      }

      @Test
      @DisplayName("Should handle query conversion returning null")
      void shouldHandleQueryConversionReturningNull() {
        // Arrange - phrase query with no terms will return null during conversion
        PhraseQuery emptyPhrase = new PhraseQuery.Builder().build();

        // Act
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(emptyPhrase);

        // Assert
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());
      }

      @Test
      @DisplayName("Should handle searcher returning null gracefully")
      void shouldHandleSearcherReturningNull() {
        // Arrange
        Query query = new TermQuery(new Term("content", "test"));

        when(efoSearcher.searchAll(any(TermQuery.class), eq(16))).thenReturn(null);

        // Act - The method catches NullPointerException and returns empty expansion
        EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

        // Assert - Should return empty expansion, not throw exception
        assertNotNull(result);
        assertTrue(result.synonyms.isEmpty());
        assertTrue(result.efo.isEmpty());
      }

    }
  }
}
