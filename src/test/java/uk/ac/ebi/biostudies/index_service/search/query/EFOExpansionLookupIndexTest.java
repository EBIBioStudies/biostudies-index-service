package uk.ac.ebi.biostudies.index_service.search.query;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.ac.ebi.biostudies.index_service.index.IndexName;

import java.io.IOException;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOIndexFields;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link EFOExpansionLookupIndex}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EFOExpansionLookupIndex Tests")
class EFOExpansionLookupIndexTest {

  @Mock private IndexManager indexManager;
  @Mock private IndexSearcher efoSearcher;
  @Mock private StoredFields storedFields;

  private EFOExpansionLookupIndex lookupIndex;

  @BeforeEach
  void setUp() throws IOException {
    lookupIndex = new EFOExpansionLookupIndex(indexManager);

    // Setup storedFields by default
    when(efoSearcher.storedFields()).thenReturn(storedFields);
  }

  @Nested
  @DisplayName("Term Query Expansion")
  class TermQueryExpansion {

    @Test
    @DisplayName("Should expand term query with synonyms and EFO terms")
    void shouldExpandTermQueryWithSynonymsAndEfo() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "cancer"));

      // Create mock EFO index document
      Document efoDoc = new Document();
      efoDoc.add(new TextField(EFOIndexFields.TERM, "neoplasm", Field.Store.YES));
      efoDoc.add(new TextField(EFOIndexFields.TERM, "tumor", Field.Store.YES));
      efoDoc.add(new TextField(EFOIndexFields.EFO, "EFO:0000311", Field.Store.YES));
      efoDoc.add(new TextField(EFOIndexFields.EFO, "EFO:0000616", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(efoDoc);

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

      verify(indexManager).acquireSearcher(IndexName.EFO);
      verify(efoSearcher).storedFields();
      verify(storedFields).document(0);
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }

    @Test
    @DisplayName("Should return empty expansion when no matches found")
    void shouldReturnEmptyExpansionWhenNoMatches() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "unknownterm"));

      TopDocs emptyTopDocs = new TopDocs(
          new TotalHits(0, TotalHits.Relation.EQUAL_TO),
          new ScoreDoc[0]
      );

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(emptyTopDocs);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());

      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
      verify(storedFields, never()).document(anyInt());
    }

    @Test
    @DisplayName("Should expand term query with only synonyms")
    void shouldExpandWithOnlySynonyms() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("title", "disease"));

      Document efoDoc = new Document();
      efoDoc.add(new TextField(EFOIndexFields.TERM, "illness", Field.Store.YES));
      efoDoc.add(new TextField(EFOIndexFields.TERM, "disorder", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(efoDoc);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.synonyms.size());
      assertTrue(result.efo.isEmpty());
    }

    @Test
    @DisplayName("Should expand term query with only EFO terms")
    void shouldExpandWithOnlyEfoTerms() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "heart"));

      Document efoDoc = new Document();
      efoDoc.add(new TextField(EFOIndexFields.EFO, "EFO:0000815", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(efoDoc);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertEquals(1, result.efo.size());
      assertTrue(result.efo.contains("EFO:0000815"));
    }
  }

  @Nested
  @DisplayName("Phrase Query Expansion")
  class PhraseQueryExpansion {

    @Test
    @DisplayName("Should expand phrase query by converting to term query")
    void shouldExpandPhraseQuery() throws IOException {
      // Arrange
      PhraseQuery.Builder builder = new PhraseQuery.Builder();
      builder.add(new Term("content", "breast"));
      builder.add(new Term("content", "cancer"));
      Query query = builder.build();

      Document efoDoc = new Document();
      efoDoc.add(new TextField(EFOIndexFields.TERM, "mammary carcinoma", Field.Store.YES));
      efoDoc.add(new TextField(EFOIndexFields.EFO, "EFO:0000305", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(efoDoc);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.synonyms.size());
      assertEquals("mammary carcinoma", result.synonyms.toArray()[0]);
      assertEquals(1, result.efo.size());

      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }

    @Test
    @DisplayName("Should handle empty phrase query gracefully")
    void shouldHandleEmptyPhraseQuery() throws IOException {
      // Arrange
      PhraseQuery emptyPhraseQuery = new PhraseQuery.Builder().build();

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(emptyPhraseQuery);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());

      // Should not search if query conversion fails
      verify(efoSearcher, never()).search(any(Query.class), anyInt());
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }
  }

  @Nested
  @DisplayName("Wildcard and Prefix Query Expansion")
  class WildcardAndPrefixExpansion {

    @Test
    @DisplayName("Should expand prefix query")
    void shouldExpandPrefixQuery() throws IOException {
      // Arrange
      Query query = new PrefixQuery(new Term("content", "diab"));

      Document efoDoc = new Document();
      efoDoc.add(new TextField(EFOIndexFields.TERM, "diabetes mellitus", Field.Store.YES));
      efoDoc.add(new TextField(EFOIndexFields.EFO, "EFO:0000400", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(PrefixQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(efoDoc);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.synonyms.size());
      assertEquals(1, result.efo.size());
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }

    @Test
    @DisplayName("Should expand wildcard query")
    void shouldExpandWildcardQuery() throws IOException {
      // Arrange
      Query query = new WildcardQuery(new Term("content", "neur*"));

      Document efoDoc = new Document();
      efoDoc.add(new TextField(EFOIndexFields.TERM, "neuron", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(WildcardQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(efoDoc);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.synonyms.size());
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }
  }

  @Nested
  @DisplayName("Fuzzy Query Expansion")
  class FuzzyQueryExpansion {

    @Test
    @DisplayName("Should expand fuzzy query")
    void shouldExpandFuzzyQuery() throws IOException {
      // Arrange
      Query query = new FuzzyQuery(new Term("content", "cancr"));

      Document efoDoc = new Document();
      efoDoc.add(new TextField(EFOIndexFields.TERM, "cancer", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(FuzzyQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(efoDoc);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.synonyms.size());
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }
  }

  @Nested
  @DisplayName("Range Query Expansion")
  class RangeQueryExpansion {

    @Test
    @DisplayName("Should expand term range query")
    void shouldExpandTermRangeQuery() throws IOException {
      // Arrange
      Query query = TermRangeQuery.newStringRange("content", "a", "d", true, true);

      Document efoDoc = new Document();
      efoDoc.add(new TextField(EFOIndexFields.TERM, "abnormality", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermRangeQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(efoDoc);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.synonyms.size());
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }
  }

  @Nested
  @DisplayName("Multiple Documents")
  class MultipleDocuments {

    @Test
    @DisplayName("Should aggregate expansion terms from multiple documents")
    void shouldAggregateFromMultipleDocuments() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "brain"));

      Document doc1 = new Document();
      doc1.add(new TextField(EFOIndexFields.TERM, "cerebrum", Field.Store.YES));
      doc1.add(new TextField(EFOIndexFields.EFO, "EFO:0000302", Field.Store.YES));

      Document doc2 = new Document();
      doc2.add(new TextField(EFOIndexFields.TERM, "encephalon", Field.Store.YES));
      doc2.add(new TextField(EFOIndexFields.EFO, "EFO:0000908", Field.Store.YES));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f), new ScoreDoc(1, 0.9f)};
      TopDocs topDocs = new TopDocs(new TotalHits(2, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(doc1);
      when(storedFields.document(1)).thenReturn(doc2);

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

      verify(storedFields).document(0);
      verify(storedFields).document(1);
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Should return empty expansion on IOException")
    void shouldReturnEmptyExpansionOnIOException() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "test"));

      when(indexManager.acquireSearcher(IndexName.EFO))
          .thenThrow(new IOException("Index not found"));

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());

      // Should not attempt to release if acquire failed
      verify(indexManager, never()).releaseSearcher(any(), any());
    }

    @Test
    @DisplayName("Should handle search exception gracefully")
    void shouldHandleSearchException() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "test"));

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(Query.class), anyInt()))
          .thenThrow(new IOException("Search failed"));

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());

      // Should still release searcher even on error
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }

    @Test
    @DisplayName("Should handle release exception gracefully")
    void shouldHandleReleaseException() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "test"));

      TopDocs emptyTopDocs = new TopDocs(
          new TotalHits(0, TotalHits.Relation.EQUAL_TO),
          new ScoreDoc[0]
      );

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(Query.class), anyInt())).thenReturn(emptyTopDocs);
      doThrow(new IOException("Release failed"))
          .when(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));

      // Act - should not throw exception
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }

    @Test
    @DisplayName("Should handle unsupported query type")
    void shouldHandleUnsupportedQueryType() throws IOException {
      // Arrange
      Query unsupportedQuery = new MatchAllDocsQuery();

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(unsupportedQuery);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());

      // Should not search for unsupported query types
      verify(efoSearcher, never()).search(any(Query.class), anyInt());
      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }

    @Test
    @DisplayName("Should handle document retrieval exception")
    void shouldHandleDocumentRetrievalException() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "test"));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenThrow(new IOException("Document not found"));

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());

      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }

    @Test
    @DisplayName("Should handle storedFields() exception")
    void shouldHandleStoredFieldsException() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "test"));

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(topDocs);
      when(efoSearcher.storedFields()).thenThrow(new IOException("Cannot get stored fields"));

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());

      verify(indexManager).releaseSearcher(eq(IndexName.EFO), eq(efoSearcher));
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Should handle null query gracefully")
    void shouldHandleNullQuery() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(null);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());
    }

    @Test
    @DisplayName("Should handle document with no expansion fields")
    void shouldHandleDocumentWithNoFields() throws IOException {
      // Arrange
      Query query = new TermQuery(new Term("content", "test"));

      Document emptyDoc = new Document();

      ScoreDoc[] scoreDocs = {new ScoreDoc(0, 1.0f)};
      TopDocs topDocs = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
      when(efoSearcher.search(any(TermQuery.class), eq(16))).thenReturn(topDocs);
      when(storedFields.document(0)).thenReturn(emptyDoc);

      // Act
      EFOExpansionTerms result = lookupIndex.getExpansionTerms(query);

      // Assert
      assertNotNull(result);
      assertTrue(result.synonyms.isEmpty());
      assertTrue(result.efo.isEmpty());
    }
  }
}
