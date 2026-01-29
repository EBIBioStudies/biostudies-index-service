package uk.ac.ebi.biostudies.index_service.search.preprocessing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.search.SearchRequest;

@DisplayName("QueryPreprocessor Tests")
class QueryPreprocessorTest {

  private QueryPreprocessor preprocessor;

  @BeforeEach
  void setUp() {
    preprocessor = new QueryPreprocessor();
  }

  @Nested
  @DisplayName("Query Normalization")
  class QueryNormalization {

    @Test
    @DisplayName("Should handle null query as empty string")
    void shouldHandleNullQueryAsEmpty() {
      SearchRequest request = new SearchRequest();
      request.setQuery(null);

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals("", result.getQuery());
    }

    @Test
    @DisplayName("Should trim whitespace from query")
    void shouldTrimWhitespace() {
      SearchRequest request = new SearchRequest();
      request.setQuery("  cancer research  ");

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals("cancer research", result.getQuery());
    }

    @Test
    @DisplayName("Should preserve query as-is when valid")
    void shouldPreserveValidQuery() {
      SearchRequest request = new SearchRequest();
      request.setQuery("author:smith AND title:bacteria");

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals("author:smith AND title:bacteria", result.getQuery());
    }
  }

  @Nested
  @DisplayName("Sorting Defaults")
  class SortingDefaults {

    @Test
    @DisplayName("Empty query with no sort should default to releaseDate descending")
    void emptyQueryShouldDefaultToReleaseDate() {
      SearchRequest request = new SearchRequest();
      request.setQuery("");
      request.setSortBy("");

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals("releaseDate", result.getSortBy());
      assertEquals("descending", result.getSortOrder());
      assertFalse(result.isHighlightingEnabled());
    }

    @Test
    @DisplayName("Non-empty query with no sort should default to relevance")
    void nonEmptyQueryShouldDefaultToRelevance() {
      SearchRequest request = new SearchRequest();
      request.setQuery("cancer");
      request.setSortBy("");

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals("relevance", result.getSortBy());
      assertEquals("descending", result.getSortOrder());
      assertTrue(result.isHighlightingEnabled());
    }

    @Test
    @DisplayName("Should preserve explicit sort field")
    void shouldPreserveExplicitSort() {
      SearchRequest request = new SearchRequest();
      request.setQuery("cancer");
      request.setSortBy("accession");
      request.setSortOrder("ascending");

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals("accession", result.getSortBy());
      assertEquals("ascending", result.getSortOrder());
    }

    @Test
    @DisplayName("Should default to descending when sort order not specified")
    void shouldDefaultToDescending() {
      SearchRequest request = new SearchRequest();
      request.setQuery("cancer");
      request.setSortBy("title");

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals("descending", result.getSortOrder());
    }
  }

  @Nested
  @DisplayName("Pagination Normalization")
  class PaginationNormalization {

    @Test
    @DisplayName("Should use default page when not specified")
    void shouldUseDefaultPage() {
      SearchRequest request = new SearchRequest();

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals(1, result.getPage());
    }

    @Test
    @DisplayName("Should use default page size when not specified")
    void shouldUseDefaultPageSize() {
      SearchRequest request = new SearchRequest();

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals(20, result.getPageSize());
    }

    @Test
    @DisplayName("Should limit page size to maximum")
    void shouldLimitPageSizeToMaximum() {
      SearchRequest request = new SearchRequest();
      request.setPageSize(5000);

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals(1000, result.getPageSize());
    }

    @Test
    @DisplayName("Should normalize invalid page to default")
    void shouldNormalizeInvalidPage() {
      SearchRequest request = new SearchRequest();
      request.setPage(0);

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals(1, result.getPage());
    }

    @Test
    @DisplayName("Should preserve valid pagination values")
    void shouldPreserveValidPagination() {
      SearchRequest request = new SearchRequest();
      request.setPage(3);
      request.setPageSize(50);

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals(3, result.getPage());
      assertEquals(50, result.getPageSize());
    }
  }

  @Nested
  @DisplayName("Highlighting Logic")
  class HighlightingLogic {

    @Test
    @DisplayName("Should disable highlighting for empty query")
    void shouldDisableHighlightingForEmptyQuery() {
      SearchRequest request = new SearchRequest();
      request.setQuery("");

      SearchRequest result = preprocessor.preprocess(request);

      assertFalse(result.isHighlightingEnabled());
    }

    @Test
    @DisplayName("Should enable highlighting for non-empty query")
    void shouldEnableHighlightingForNonEmptyQuery() {
      SearchRequest request = new SearchRequest();
      request.setQuery("cancer research");

      SearchRequest result = preprocessor.preprocess(request);

      assertTrue(result.isHighlightingEnabled());
    }
  }

  @Nested
  @DisplayName("Field Preservation")
  class FieldPreservation {

    @Test
    @DisplayName("Should preserve collection field")
    void shouldPreserveCollection() {
      SearchRequest request = new SearchRequest();
      request.setCollection("biostudies");

      SearchRequest result = preprocessor.preprocess(request);

      assertEquals("biostudies", result.getCollection());
    }
  }
}
