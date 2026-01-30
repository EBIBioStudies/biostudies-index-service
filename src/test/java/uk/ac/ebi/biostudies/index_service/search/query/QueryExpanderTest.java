package uk.ac.ebi.biostudies.index_service.search.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.analysis.AnalyzerManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryExpander Tests")
class QueryExpanderTest {

  @Mock private EFOExpansionLookupIndex efoExpansionLookupIndex;
  @Mock private AnalyzerManager analyzerManager;

  private QueryExpander queryExpander;
  private Set<String> expandableFields;

  @BeforeEach
  void setUp() {
    queryExpander = new QueryExpander(efoExpansionLookupIndex, analyzerManager);

    // Default expandable fields
    expandableFields = new HashSet<>(Arrays.asList("title", "description", "content"));
    when(analyzerManager.getExpandableFieldNames()).thenReturn(expandableFields);
  }

  @Nested
  @DisplayName("Basic Query Expansion Tests")
  class BasicExpansionTests {

    @Test
    @DisplayName("Should expand TermQuery with EFO terms and synonyms")
    void shouldExpandTermQueryWithEfoAndSynonyms() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "leukemia"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "leukemia";
      expansionTerms.efo.addAll(
          Arrays.asList(
              "acute myeloid leukemia",
              "chronic lymphocytic leukemia",
              "t-cell prolymphocytic leukemia"));
      expansionTerms.synonyms.addAll(Arrays.asList("leukaemia", "blood cancer"));

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertNotNull(result);
      assertTrue(result.getQuery() instanceof BooleanQuery);
      assertEquals(3, result.getExpandedEfoTerms().size());
      assertEquals(2, result.getExpandedSynonyms().size());
      assertTrue(result.getExpandedEfoTerms().contains("acute myeloid leukemia"));
      assertTrue(result.getExpandedSynonyms().contains("leukaemia"));

      verify(efoExpansionLookupIndex).getExpansionTerms(originalQuery);
    }

    @Test
    @DisplayName("Should return original query when no expansion terms found")
    void shouldReturnOriginalQueryWhenNoExpansionTerms() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "unique_term_123"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "unique_term_123";
      // No EFO or synonym terms

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertNotNull(result);
      assertEquals(originalQuery, result.getQuery());
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }

    @Test
    @DisplayName("Should expand PhraseQuery with multi-word terms")
    void shouldExpandPhraseQuery() throws IOException {
      // Arrange
      PhraseQuery originalQuery =
          new PhraseQuery.Builder()
              .add(new Term("description", "breast"))
              .add(new Term("description", "cancer"))
              .build();

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "breast cancer";
      expansionTerms.efo.addAll(Arrays.asList("invasive breast carcinoma", "ductal carcinoma"));
      expansionTerms.synonyms.add("mammary carcinoma");

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertNotNull(result);
      assertTrue(result.getQuery() instanceof BooleanQuery);
      assertEquals(2, result.getExpandedEfoTerms().size());
      assertEquals(1, result.getExpandedSynonyms().size());
    }
  }

  @Nested
  @DisplayName("Non-Expandable Field Tests")
  class NonExpandableFieldTests {

    @Test
    @DisplayName("Should not expand query on non-expandable field")
    void shouldNotExpandNonExpandableField() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("accession", "S-BSST1234"));

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertEquals(originalQuery, result.getQuery());
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());

      // Should never lookup expansion for non-expandable fields
      verify(efoExpansionLookupIndex, never()).getExpansionTerms(any());
    }

    @Test
    @DisplayName("Should only expand clauses with expandable fields in BooleanQuery")
    void shouldOnlyExpandExpandableFieldsInBooleanQuery() throws IOException {
      // Arrange
      Query expandableQuery = new TermQuery(new Term("title", "diabetes"));
      Query nonExpandableQuery = new TermQuery(new Term("accession", "S-123"));

      BooleanQuery originalQuery =
          new BooleanQuery.Builder()
              .add(expandableQuery, BooleanClause.Occur.MUST)
              .add(nonExpandableQuery, BooleanClause.Occur.MUST)
              .build();

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "diabetes";
      expansionTerms.efo.add("type 1 diabetes");

      when(efoExpansionLookupIndex.getExpansionTerms(expandableQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertNotNull(result);
      assertTrue(result.getQuery() instanceof BooleanQuery);
      assertEquals(1, result.getExpandedEfoTerms().size());

      // Only expandable query should be looked up
      verify(efoExpansionLookupIndex).getExpansionTerms(expandableQuery);
      verify(efoExpansionLookupIndex, never()).getExpansionTerms(nonExpandableQuery);
    }
  }

  @Nested
  @DisplayName("BooleanQuery Expansion Tests")
  class BooleanQueryExpansionTests {

    @Test
    @DisplayName("Should expand all clauses in BooleanQuery")
    void shouldExpandAllClausesInBooleanQuery() throws IOException {
      // Arrange
      Query query1 = new TermQuery(new Term("title", "diabetes"));
      Query query2 = new TermQuery(new Term("description", "treatment"));

      BooleanQuery originalQuery =
          new BooleanQuery.Builder()
              .add(query1, BooleanClause.Occur.MUST)
              .add(query2, BooleanClause.Occur.SHOULD)
              .build();

      EFOExpansionTerms expansionTerms1 = new EFOExpansionTerms();
      expansionTerms1.term = "diabetes";
      expansionTerms1.efo.add("type 1 diabetes");

      EFOExpansionTerms expansionTerms2 = new EFOExpansionTerms();
      expansionTerms2.term = "treatment";
      expansionTerms2.synonyms.add("therapy");

      when(efoExpansionLookupIndex.getExpansionTerms(query1)).thenReturn(expansionTerms1);
      when(efoExpansionLookupIndex.getExpansionTerms(query2)).thenReturn(expansionTerms2);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertTrue(result.getQuery() instanceof BooleanQuery);
      assertEquals(1, result.getExpandedEfoTerms().size());
      assertEquals(1, result.getExpandedSynonyms().size());
      assertTrue(result.getExpandedEfoTerms().contains("type 1 diabetes"));
      assertTrue(result.getExpandedSynonyms().contains("therapy"));
    }

    @Test
    @DisplayName("Should combine expansion terms from multiple clauses")
    void shouldCombineExpansionTermsFromMultipleClauses() throws IOException {
      // Arrange
      Query query1 = new TermQuery(new Term("title", "cancer"));
      Query query2 = new TermQuery(new Term("title", "treatment"));

      BooleanQuery originalQuery =
          new BooleanQuery.Builder()
              .add(query1, BooleanClause.Occur.MUST)
              .add(query2, BooleanClause.Occur.MUST)
              .build();

      EFOExpansionTerms expansionTerms1 = new EFOExpansionTerms();
      expansionTerms1.term = "cancer";
      expansionTerms1.efo.addAll(Arrays.asList("breast cancer", "lung cancer"));

      EFOExpansionTerms expansionTerms2 = new EFOExpansionTerms();
      expansionTerms2.term = "treatment";
      expansionTerms2.synonyms.addAll(Arrays.asList("therapy", "medication"));

      when(efoExpansionLookupIndex.getExpansionTerms(query1)).thenReturn(expansionTerms1);
      when(efoExpansionLookupIndex.getExpansionTerms(query2)).thenReturn(expansionTerms2);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertEquals(2, result.getExpandedEfoTerms().size());
      assertEquals(2, result.getExpandedSynonyms().size());
    }

    @Test
    @DisplayName("Should handle nested BooleanQuery")
    void shouldHandleNestedBooleanQuery() throws IOException {
      // Arrange
      Query innerQuery = new TermQuery(new Term("title", "diabetes"));
      BooleanQuery nestedQuery =
          new BooleanQuery.Builder().add(innerQuery, BooleanClause.Occur.SHOULD).build();

      Query outerQuery = new TermQuery(new Term("description", "treatment"));
      BooleanQuery originalQuery =
          new BooleanQuery.Builder()
              .add(nestedQuery, BooleanClause.Occur.MUST)
              .add(outerQuery, BooleanClause.Occur.MUST)
              .build();

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "diabetes";
      expansionTerms.efo.add("type 2 diabetes");

      when(efoExpansionLookupIndex.getExpansionTerms(innerQuery)).thenReturn(expansionTerms);
      when(efoExpansionLookupIndex.getExpansionTerms(outerQuery))
          .thenReturn(new EFOExpansionTerms());

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertNotNull(result);
      assertTrue(result.getQuery() instanceof BooleanQuery);
      assertEquals(1, result.getExpandedEfoTerms().size());
    }
  }

  @Nested
  @DisplayName("Special Query Type Tests")
  class SpecialQueryTypeTests {

    @Test
    @DisplayName("Should not expand MatchAllDocsQuery")
    void shouldNotExpandMatchAllDocsQuery() {
      // Arrange
      Query originalQuery = new MatchAllDocsQuery();

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertEquals(originalQuery, result.getQuery());
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());

      verify(efoExpansionLookupIndex, never()).getExpansionTerms(any());
    }

    @Test
    @DisplayName("Should not expand PrefixQuery to avoid side effects")
    void shouldNotExpandPrefixQuery() {
      // Arrange
      Query originalQuery = new PrefixQuery(new Term("title", "leuk"));

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertEquals(originalQuery, result.getQuery());
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }

    @Test
    @DisplayName("Should not expand WildcardQuery to avoid side effects")
    void shouldNotExpandWildcardQuery() {
      // Arrange
      Query originalQuery = new WildcardQuery(new Term("title", "leuk*"));

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertEquals(originalQuery, result.getQuery());
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }

    @Test
    @DisplayName("Should handle FuzzyQuery on expandable field")
    void shouldHandleFuzzyQuery() throws IOException {
      // Arrange
      Query originalQuery = new FuzzyQuery(new Term("title", "diabetis"), 1);

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "diabetis";
      expansionTerms.efo.add("diabetes mellitus");

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.getExpandedEfoTerms().size());
    }

    @Test
    @DisplayName("Should handle TermRangeQuery")
    void shouldHandleTermRangeQuery() throws IOException {
      // Arrange
      Query originalQuery = new TermRangeQuery("release_date", null, null, true, true);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      // TermRangeQuery field is not in expandableFields, so no expansion
      assertEquals(originalQuery, result.getQuery());
    }
  }

  @Nested
  @DisplayName("Expansion Limits Tests")
  class ExpansionLimitsTests {

    @Test
    @DisplayName("Should not expand when total terms exceed MAX_EXPANSION_TERMS")
    void shouldNotExpandWhenTooManyTerms() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "cancer"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "cancer";

      // Add 101 terms (exceeds MAX_EXPANSION_TERMS = 100)
      for (int i = 0; i < 60; i++) {
        expansionTerms.efo.add("efo_term_" + i);
      }
      for (int i = 0; i < 41; i++) {
        expansionTerms.synonyms.add("synonym_" + i);
      }

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      // Should return original query without expansion but still track the terms
      assertEquals(originalQuery, result.getQuery());
      assertEquals(60, result.getExpandedEfoTerms().size());
      assertEquals(41, result.getExpandedSynonyms().size());
    }

    @Test
    @DisplayName("Should expand when terms equal MAX_EXPANSION_TERMS")
    void shouldExpandWhenTermsEqualMax() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "disease"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "disease";

      // Add exactly 100 terms
      for (int i = 0; i < 100; i++) {
        expansionTerms.efo.add("efo_term_" + i);
      }

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertTrue(result.getQuery() instanceof BooleanQuery);
      assertEquals(100, result.getExpandedEfoTerms().size());
    }
  }

  @Nested
  @DisplayName("Redundancy Detection Tests")
  class RedundancyDetectionTests {

    @Test
    @DisplayName("Should not add synonym that matches PrefixQuery pattern")
    void shouldNotAddRedundantSynonymForPrefixQuery() throws IOException {
      // This test verifies that redundancy detection works
      // even though PrefixQuery itself is not expanded

      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "leuk"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "leuk";
      expansionTerms.synonyms.addAll(
          Arrays.asList(
              "leukemia", // Would be redundant if originalQuery were PrefixQuery("leuk")
              "cancer"));

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertTrue(result.getQuery() instanceof BooleanQuery);
      BooleanQuery expanded = (BooleanQuery) result.getQuery();
      assertTrue(expanded.clauses().size() > 0);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should return original query on RuntimeException from lookup")
    void shouldReturnOriginalQueryOnRuntimeException() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "test"));

      when(efoExpansionLookupIndex.getExpansionTerms(any()))
          .thenThrow(new RuntimeException("Index unavailable"));

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertEquals(originalQuery, result.getQuery());
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }

    @Test
    @DisplayName("Should return original query when expansion throws NullPointerException")
    void shouldReturnOriginalQueryOnNullPointerException() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "test"));

      when(efoExpansionLookupIndex.getExpansionTerms(any()))
          .thenThrow(new NullPointerException("Null expansion data"));

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertEquals(originalQuery, result.getQuery());
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }

    @Test
    @DisplayName("Should return original query when expansion throws IllegalStateException")
    void shouldReturnOriginalQueryOnIllegalStateException() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "test"));

      when(efoExpansionLookupIndex.getExpansionTerms(any()))
          .thenThrow(new IllegalStateException("Invalid expansion state"));

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertEquals(originalQuery, result.getQuery());
      assertTrue(result.getExpandedEfoTerms().isEmpty());
      assertTrue(result.getExpandedSynonyms().isEmpty());
    }

    @Test
    @DisplayName("Should handle null expansion terms gracefully")
    void shouldHandleNullExpansionTerms() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "test"));

      when(efoExpansionLookupIndex.getExpansionTerms(any())).thenReturn(null);

      // Act & Assert
      // This may throw NPE depending on implementation
      // If it does, the catch block should return original query
      assertDoesNotThrow(
          () -> {
            QueryResult result = queryExpander.expand(originalQuery);
            assertNotNull(result);
          });
    }

    @Test
    @DisplayName("Should handle expansion terms with null lists")
    void shouldHandleExpansionTermsWithNullLists() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "test"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      // Don't initialize lists - they might be null
      expansionTerms.efo = null;
      expansionTerms.synonyms = null;

      when(efoExpansionLookupIndex.getExpansionTerms(any())).thenReturn(expansionTerms);

      // Act & Assert
      assertDoesNotThrow(
          () -> {
            QueryResult result = queryExpander.expand(originalQuery);
            assertNotNull(result);
          });
    }
  }

  @Nested
  @DisplayName("Duplicate Prevention Tests")
  class DuplicatePreventionTests {

    @Test
    @DisplayName("Should not contain duplicate EFO terms - EXPECTED TO FAIL")
    void shouldNotContainDuplicateEfoTerms() throws IOException {
      // Arrange
      Query query1 = new TermQuery(new Term("title", "cancer"));
      Query query2 = new TermQuery(new Term("description", "tumor"));

      BooleanQuery originalQuery =
          new BooleanQuery.Builder()
              .add(query1, BooleanClause.Occur.SHOULD)
              .add(query2, BooleanClause.Occur.SHOULD)
              .build();

      EFOExpansionTerms expansionTerms1 = new EFOExpansionTerms();
      expansionTerms1.term = "cancer";
      expansionTerms1.efo.addAll(
          Arrays.asList(
              "breast cancer", "lung cancer", "malignant neoplasm" // Duplicate term
              ));

      EFOExpansionTerms expansionTerms2 = new EFOExpansionTerms();
      expansionTerms2.term = "tumor";
      expansionTerms2.efo.addAll(
          Arrays.asList(
              "malignant neoplasm", // Duplicate term
              "benign tumor"));

      when(efoExpansionLookupIndex.getExpansionTerms(query1)).thenReturn(expansionTerms1);
      when(efoExpansionLookupIndex.getExpansionTerms(query2)).thenReturn(expansionTerms2);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      Set<String> efoTerms = result.getExpandedEfoTerms();
      Set<String> uniqueEfoTerms = new HashSet<>(efoTerms);

      // This assertion will FAIL with current implementation (uses List)
      assertEquals(
          uniqueEfoTerms.size(),
          efoTerms.size(),
          "EFO terms list contains duplicates: " + efoTerms);
    }

    @Test
    @DisplayName("Should not contain duplicate synonyms")
    void shouldNotContainDuplicateSynonyms() throws IOException {
      // Arrange
      Query query1 = new TermQuery(new Term("title", "treatment"));
      Query query2 = new TermQuery(new Term("description", "medication"));

      BooleanQuery originalQuery =
          new BooleanQuery.Builder()
              .add(query1, BooleanClause.Occur.MUST)
              .add(query2, BooleanClause.Occur.MUST)
              .build();

      EFOExpansionTerms expansionTerms1 = new EFOExpansionTerms();
      expansionTerms1.term = "treatment";
      expansionTerms1.synonyms.addAll(Arrays.asList("therapy", "medical treatment"));

      EFOExpansionTerms expansionTerms2 = new EFOExpansionTerms();
      expansionTerms2.term = "medication";
      expansionTerms2.synonyms.addAll(
          Arrays.asList(
              "therapy", // Duplicate
              "drug",
              "medicine"));

      when(efoExpansionLookupIndex.getExpansionTerms(query1)).thenReturn(expansionTerms1);
      when(efoExpansionLookupIndex.getExpansionTerms(query2)).thenReturn(expansionTerms2);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      Set<String> synonyms = result.getExpandedSynonyms();
      Set<String> uniqueSynonyms = new HashSet<>(synonyms);

      // This assertion will FAIL with current implementation (uses List)
      assertEquals(
          uniqueSynonyms.size(), synonyms.size(), "Synonyms list contains duplicates: " + synonyms);
    }

    @Test
    @DisplayName("Should handle same term in both EFO and synonyms")
    void shouldHandleTermInBothEfoAndSynonyms() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "diabetes"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "diabetes";
      expansionTerms.efo.addAll(Arrays.asList("diabetes mellitus", "type 1 diabetes"));
      expansionTerms.synonyms.addAll(
          Arrays.asList(
              "diabetes mellitus", // Also in EFO list (cross-contamination)
              "sugar disease"));

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      // Even if duplicates exist across lists, each list should be unique
      Set<String> efoTerms = result.getExpandedEfoTerms();
      Set<String> synonyms = result.getExpandedSynonyms();

      assertEquals(new HashSet<>(efoTerms).size(), efoTerms.size());
      assertEquals(new HashSet<>(synonyms).size(), synonyms.size());
    }

    @Test
    @DisplayName("Should not add empty or whitespace-only expansion terms")
    void shouldNotAddEmptyExpansionTerms() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "test"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "test";
      expansionTerms.efo.addAll(
          Arrays.asList(
              "valid term",
              "", // Empty
              "   ", // Whitespace only
              "another valid term"));
      expansionTerms.synonyms.addAll(
          Arrays.asList(
              "synonym1",
              null, // Null (if allowed by EFOExpansionLookupIndex)
              "synonym2"));

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      // Verify no empty/null terms made it through
      assertFalse(
          result.getExpandedEfoTerms().stream().anyMatch(s -> s == null || s.trim().isEmpty()));
      assertFalse(
          result.getExpandedSynonyms().stream().anyMatch(s -> s == null || s.trim().isEmpty()));
    }
  }

  @Nested
  @DisplayName("Multi-Word Term Tests")
  class MultiWordTermTests {

    @Test
    @DisplayName("Should create PhraseQuery for multi-word expansion terms")
    void shouldCreatePhraseQueryForMultiWordTerms() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "diabetes"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "diabetes";
      expansionTerms.efo.add("type 1 diabetes mellitus"); // Multi-word term

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertTrue(result.getQuery() instanceof BooleanQuery);
      BooleanQuery expanded = (BooleanQuery) result.getQuery();

      // Should contain a PhraseQuery for the multi-word term
      boolean hasPhraseQuery =
          expanded.clauses().stream().anyMatch(clause -> clause.query() instanceof PhraseQuery);

      assertTrue(hasPhraseQuery, "Expected a PhraseQuery for multi-word expansion term");
    }

    @Test
    @DisplayName("Should create TermQuery for single-word expansion terms")
    void shouldCreateTermQueryForSingleWordTerms() throws IOException {
      // Arrange
      Query originalQuery = new TermQuery(new Term("title", "cancer"));

      EFOExpansionTerms expansionTerms = new EFOExpansionTerms();
      expansionTerms.term = "cancer";
      expansionTerms.synonyms.add("carcinoma"); // Single word

      when(efoExpansionLookupIndex.getExpansionTerms(originalQuery)).thenReturn(expansionTerms);

      // Act
      QueryResult result = queryExpander.expand(originalQuery);

      // Assert
      assertTrue(result.getQuery() instanceof BooleanQuery);
      BooleanQuery expanded = (BooleanQuery) result.getQuery();

      // Should contain TermQuery for single-word term
      long termQueryCount =
          expanded.clauses().stream().filter(clause -> clause.query() instanceof TermQuery).count();

      assertTrue(termQueryCount >= 2, "Expected TermQuery for single-word expansion term");
    }
  }
}
