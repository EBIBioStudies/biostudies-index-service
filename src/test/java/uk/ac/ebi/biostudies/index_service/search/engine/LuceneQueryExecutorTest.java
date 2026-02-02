package uk.ac.ebi.biostudies.index_service.search.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("LuceneQueryExecutor Tests")
class LuceneQueryExecutorTest {

  @Mock private IndexManager indexManager;
  @Mock private IndexSearcher mockSearcher;
  @Mock private StoredFields mockStoredFields;

  private LuceneQueryExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new LuceneQueryExecutor(indexManager);
  }

  @Nested
  @DisplayName("Unit Tests with Mocks")
  class UnitTests {

    @Test
    @DisplayName("Should execute paginated query and return results with Documents")
    void shouldExecutePaginatedQueryAndReturnResults() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = new SearchCriteria.Builder(query)
          .page(1, 10)
          .build();

      ScoreDoc[] scoreDocs = new ScoreDoc[10];
      for (int i = 0; i < 10; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(100, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 10)).thenReturn(topDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      for (int i = 0; i < 10; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.page());
      assertEquals(10, result.pageSize());
      assertEquals(100, result.totalHits());
      assertEquals(10, result.results().size());
      assertTrue(result.isTotalHitsExact());

      Document firstDoc = result.results().get(0);
      assertEquals("S-ACC0", firstDoc.get("accession"));
      assertEquals("study", firstDoc.get("type"));
      assertEquals("Title 0", firstDoc.get("title"));

      verify(indexManager).acquireSearcher(IndexName.SUBMISSION);
      verify(indexManager).releaseSearcher(IndexName.SUBMISSION, mockSearcher);
    }

    @Test
    @DisplayName("Should execute non-paginated query and return all results")
    void shouldExecuteNonPaginatedQuery() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = SearchCriteria.of(query);

      ScoreDoc[] scoreDocs = new ScoreDoc[50];
      for (int i = 0; i < 50; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(50, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 10000)).thenReturn(topDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      for (int i = 0; i < 50; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.page());
      assertEquals(50, result.pageSize());
      assertEquals(50, result.results().size());
      assertEquals("S-ACC0", result.results().get(0).get("accession"));
      assertEquals("S-ACC49", result.results().get(49).get("accession"));

      verify(mockSearcher).search(query, 10000);
    }

    @Test
    @DisplayName("Should execute query with sorting")
    void shouldExecuteQueryWithSorting() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      Sort sort = new Sort(new SortField("title", SortField.Type.STRING));
      SearchCriteria criteria = new SearchCriteria.Builder(query)
          .page(1, 20)
          .sort(sort)
          .build();

      ScoreDoc[] scoreDocs = new ScoreDoc[5];
      for (int i = 0; i < 5; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopFieldDocs topFieldDocs =
          new TopFieldDocs(
              new TotalHits(50, TotalHits.Relation.EQUAL_TO),
              scoreDocs,
              new SortField[] {new SortField("title", SortField.Type.STRING)});

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 20, sort)).thenReturn(topFieldDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      for (int i = 0; i < 5; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertNotNull(result);
      assertEquals(50, result.totalHits());
      assertEquals(5, result.results().size());
      verify(mockSearcher).search(query, 20, sort);
    }

    @Test
    @DisplayName("Should execute non-paginated query with sorting")
    void shouldExecuteNonPaginatedQueryWithSorting() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      Sort sort = new Sort(new SortField("title", SortField.Type.STRING));
      SearchCriteria criteria = new SearchCriteria.Builder(query)
          .sort(sort)
          .build();

      ScoreDoc[] scoreDocs = new ScoreDoc[25];
      for (int i = 0; i < 25; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopFieldDocs topFieldDocs =
          new TopFieldDocs(
              new TotalHits(25, TotalHits.Relation.EQUAL_TO),
              scoreDocs,
              new SortField[] {new SortField("title", SortField.Type.STRING)});

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 10000, sort)).thenReturn(topFieldDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      for (int i = 0; i < 25; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertEquals(25, result.results().size());
      verify(mockSearcher).search(query, 10000, sort);
    }

    @Test
    @DisplayName("Should handle page 2 correctly - only return current page documents")
    void shouldHandleSecondPageCorrectly() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = new SearchCriteria.Builder(query)
          .page(2, 20)
          .build();

      ScoreDoc[] scoreDocs = new ScoreDoc[40];
      for (int i = 0; i < 40; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(100, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 40)).thenReturn(topDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      for (int i = 20; i < 40; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertEquals(2, result.page());
      assertEquals(20, result.pageSize());
      assertEquals(20, result.results().size());
      assertEquals("S-ACC20", result.results().get(0).get("accession"));
      assertEquals("S-ACC39", result.results().get(19).get("accession"));
      verify(mockSearcher).search(query, 40);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for excessive page size")
    void shouldThrowExceptionForExcessivePageSize() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = new SearchCriteria.Builder(query)
          .page(1, 5000) // Exceeds MAX_PAGE_SIZE of 1000
          .build();

      // Act & Assert
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> executor.execute(IndexName.SUBMISSION, criteria));

      assertTrue(ex.getMessage().contains("pageSize exceeds maximum"));

      // Validation fails before acquiring a searcher, so no acquire/release should happen
      verify(indexManager, never()).acquireSearcher(any());
      verify(indexManager, never()).releaseSearcher(any(), any());
    }

    @Test
    @DisplayName("Should always release searcher even when exception occurs")
    void shouldAlwaysReleaseSearcherOnException() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = SearchCriteria.of(query);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(any(Query.class), anyInt()))
          .thenThrow(new IOException("Search failed"));

      // Act & Assert
      assertThrows(IOException.class,
          () -> executor.execute(IndexName.SUBMISSION, criteria));

      verify(indexManager).releaseSearcher(IndexName.SUBMISSION, mockSearcher);
    }

    @Test
    @DisplayName("Should handle estimate total hits")
    void shouldHandleEstimateTotalHits() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = new SearchCriteria.Builder(query)
          .page(1, 10)
          .build();

      ScoreDoc[] scoreDocs = new ScoreDoc[10];
      for (int i = 0; i < 10; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs =
          new TopDocs(new TotalHits(1000, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 10)).thenReturn(topDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      for (int i = 0; i < 10; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertFalse(result.isTotalHitsExact());
      assertEquals(1000, result.totalHits());
    }

    @Test
    @DisplayName("Non-paginated query should limit to DEFAULT_MAX_RESULTS")
    void nonPaginatedQueryShouldLimitToDefaultMaxResults() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = SearchCriteria.of(query);

      ScoreDoc[] scoreDocs = new ScoreDoc[10000];
      for (int i = 0; i < 10000; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(50000, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 10000)).thenReturn(topDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      for (int i = 0; i < 10000; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertEquals(10000, result.results().size());
      verify(mockSearcher).search(query, 10000);
    }

    private Document createMockDocument(String accession, String type, String title) {
      Document doc = new Document();
      doc.add(new StringField("accession", accession, Field.Store.YES));
      doc.add(new StringField("type", type, Field.Store.YES));
      doc.add(new TextField("title", title, Field.Store.YES));
      doc.add(new TextField("author", "Author Name", Field.Store.YES));
      doc.add(new StoredField("links", "5"));
      doc.add(new StoredField("files", "10"));
      doc.add(new StringField("release_date", "2024-01-01", Field.Store.YES));
      doc.add(new StoredField("views", "100"));
      doc.add(new StringField("isPublic", "true", Field.Store.YES));
      doc.add(new TextField("content", "Sample content", Field.Store.YES));
      return doc;
    }
  }

  @Nested
  @DisplayName("Input Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should throw NullPointerException when indexName is null")
    void shouldThrowExceptionWhenIndexNameIsNull() {
      SearchCriteria criteria = SearchCriteria.of(new MatchAllDocsQuery());

      assertThrows(NullPointerException.class,
          () -> executor.execute(null, criteria));
    }

    @Test
    @DisplayName("Should throw NullPointerException when criteria is null")
    void shouldThrowExceptionWhenCriteriaIsNull() {
      assertThrows(NullPointerException.class,
          () -> executor.execute(IndexName.SUBMISSION, null));
    }

    @Test
    @DisplayName("Should throw NullPointerException when IndexManager is null in constructor")
    void shouldThrowExceptionWhenIndexManagerIsNull() {
      assertThrows(NullPointerException.class,
          () -> new LuceneQueryExecutor(null));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for deep pagination")
    void shouldThrowExceptionForDeepPagination() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = new SearchCriteria.Builder(query)
          .page(100, 600) // 100 * 600 = 60,000 > MAX_TOTAL_DOCS_FOR_PAGINATION (50,000)
          .build();

      // Act & Assert
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> executor.execute(IndexName.SUBMISSION, criteria));

      assertTrue(ex.getMessage().contains("Deep pagination limit exceeded"));

      // Validation fails before acquiring a searcher, so no acquire/release should happen
      verify(indexManager, never()).acquireSearcher(any());
      verify(indexManager, never()).releaseSearcher(any(), any());
    }
  }

  @Nested
  @DisplayName("Integration Tests with RAMDirectory")
  class IntegrationTests {

    private ByteBuffersDirectory directory;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    @BeforeEach
    void setUpIndex() throws IOException {
      directory = new ByteBuffersDirectory();
      IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
      writer = new IndexWriter(directory, config);

      addDocument(
          "S-DOC1", "study", "Java programming tutorial", "Smith J", "java", 100L, 5, 10, 50);
      addDocument(
          "S-DOC2", "study", "Python programming guide", "Jones P", "python", 200L, 3, 8, 75);
      addDocument("S-DOC3", "study", "Java advanced concepts", "Brown A", "java", 150L, 7, 12, 120);
      addDocument("S-DOC4", "study", "Lucene search engine", "Davis L", "lucene", 300L, 4, 15, 90);
      addDocument(
          "S-DOC5", "study", "Spring framework basics", "Wilson S", "java", 250L, 6, 20, 110);

      for (int i = 6; i <= 50; i++) {
        addDocument(
            "S-DOC" + i,
            "study",
            "Test document " + i,
            "Author" + i,
            "test",
            (long) i * 10,
            i,
            i * 2,
            i * 5);
      }

      writer.commit();
      writer.close();

      reader = DirectoryReader.open(directory);
      searcher = new IndexSearcher(reader);
    }

    private void addDocument(
        String accession,
        String type,
        String title,
        String author,
        String category,
        Long timestamp,
        int links,
        int files,
        int views)
        throws IOException {
      Document doc = new Document();
      doc.add(new StringField("accession", accession, Field.Store.YES));
      doc.add(new StringField("type", type, Field.Store.YES));
      doc.add(new TextField("title", title, Field.Store.YES));
      doc.add(new TextField("author", author, Field.Store.YES));
      doc.add(new TextField("content", title + " content", Field.Store.YES));
      doc.add(new StringField("category", category, Field.Store.YES));
      doc.add(new LongPoint("timestamp", timestamp));
      doc.add(new StoredField("timestamp", timestamp));
      doc.add(new NumericDocValuesField("timestamp", timestamp));
      doc.add(new StoredField("links", String.valueOf(links)));
      doc.add(new StoredField("files", String.valueOf(files)));
      doc.add(new StringField("release_date", "2024-01-01", Field.Store.YES));
      doc.add(new StoredField("views", String.valueOf(views)));
      doc.add(new StringField("isPublic", "true", Field.Store.YES));
      writer.addDocument(doc);
    }

    @Test
    @DisplayName("Should paginate through all documents correctly")
    void shouldPaginateThroughAllDocuments() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();
      int pageSize = 10;

      // Act & Assert - Page 1
      SearchCriteria page1Criteria = new SearchCriteria.Builder(query).page(1, pageSize).build();
      PaginatedResult<Document> page1 = executor.execute(IndexName.SUBMISSION, page1Criteria);

      assertEquals(1, page1.page());
      assertEquals(10, page1.pageSize());
      assertEquals(50, page1.totalHits());
      assertTrue(page1.isTotalHitsExact());
      assertEquals(10, page1.results().size());
      assertEquals("S-DOC1", page1.results().get(0).get("accession"));

      // Act & Assert - Page 2
      SearchCriteria page2Criteria = new SearchCriteria.Builder(query).page(2, pageSize).build();
      PaginatedResult<Document> page2 = executor.execute(IndexName.SUBMISSION, page2Criteria);

      assertEquals(2, page2.page());
      assertEquals(10, page2.results().size());

      // Act & Assert - Page 5 (last page)
      SearchCriteria page5Criteria = new SearchCriteria.Builder(query).page(5, pageSize).build();
      PaginatedResult<Document> page5 = executor.execute(IndexName.SUBMISSION, page5Criteria);

      assertEquals(5, page5.page());
      assertEquals(10, page5.results().size());
    }

    @Test
    @DisplayName("Should return correct documents for specific term query")
    void shouldReturnCorrectDocumentsForTermQuery() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new TermQuery(new Term("category", "java"));
      SearchCriteria criteria = new SearchCriteria.Builder(query).page(1, 10).build();

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertEquals(3, result.totalHits());
      assertEquals(3, result.results().size());

      List<String> accessions = result.results().stream()
          .map(doc -> doc.get("accession"))
          .toList();

      assertTrue(accessions.contains("S-DOC1"));
      assertTrue(accessions.contains("S-DOC3"));
      assertTrue(accessions.contains("S-DOC5"));
    }

    @Test
    @DisplayName("Should sort results by timestamp descending")
    void shouldSortResultsByTimestampDescending() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new TermQuery(new Term("category", "java"));
      Sort sort = new Sort(new SortField("timestamp", SortField.Type.LONG, true));
      SearchCriteria criteria = new SearchCriteria.Builder(query)
          .page(1, 10)
          .sort(sort)
          .build();

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertEquals(3, result.totalHits());
      assertEquals(3, result.results().size());

      assertEquals("S-DOC5", result.results().get(0).get("accession"));
      assertEquals("S-DOC3", result.results().get(1).get("accession"));
      assertEquals("S-DOC1", result.results().get(2).get("accession"));
    }

    @Test
    @DisplayName("Should handle empty page correctly")
    void shouldHandleEmptyPageCorrectly() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = new SearchCriteria.Builder(query).page(10, 10).build();

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertEquals(10, result.page());
      assertEquals(50, result.totalHits());
      assertEquals(0, result.results().size());
    }

    @Test
    @DisplayName("Should handle partial last page correctly")
    void shouldHandlePartialLastPageCorrectly() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = new SearchCriteria.Builder(query).page(4, 15).build();

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertEquals(4, result.page());
      assertEquals(50, result.totalHits());
      assertEquals(5, result.results().size());
    }

    @Test
    @DisplayName("Should work with different index names")
    void shouldWorkWithDifferentIndexNames() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.FILES)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = new SearchCriteria.Builder(query).page(1, 10).build();

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.FILES, criteria);

      // Assert
      assertNotNull(result);
      assertEquals(10, result.results().size());
      verify(indexManager).acquireSearcher(IndexName.FILES);
      verify(indexManager).releaseSearcher(IndexName.FILES, searcher);
    }

    @Test
    @DisplayName("Non-paginated query should return all documents")
    void nonPaginatedQueryShouldReturnAllDocuments() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();
      SearchCriteria criteria = SearchCriteria.of(query);

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertNotNull(result);
      assertEquals(50, result.results().size());
      assertEquals("S-DOC1", result.results().get(0).get("accession"));
      assertEquals("S-DOC50", result.results().get(49).get("accession"));

      verify(indexManager).acquireSearcher(IndexName.SUBMISSION);
      verify(indexManager).releaseSearcher(IndexName.SUBMISSION, searcher);
    }

    @Test
    @DisplayName("Non-paginated query with term filter should return matching documents")
    void nonPaginatedQueryWithTermFilterShouldWork() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new TermQuery(new Term("category", "java"));
      SearchCriteria criteria = SearchCriteria.of(query);

      // Act
      PaginatedResult<Document> result = executor.execute(IndexName.SUBMISSION, criteria);

      // Assert
      assertNotNull(result);
      assertEquals(3, result.results().size());

      List<String> accessions = result.results().stream()
          .map(doc -> doc.get("accession"))
          .toList();

      assertTrue(accessions.contains("S-DOC1"));
      assertTrue(accessions.contains("S-DOC3"));
      assertTrue(accessions.contains("S-DOC5"));
    }
  }

  @Nested
  @DisplayName("PaginatedResult Helper Methods Tests")
  class PaginatedResultTests {

    @Test
    @DisplayName("Should use map method to transform documents")
    void shouldUseMapMethodToTransformDocuments() {
      // Arrange
      Document doc1 = new Document();
      doc1.add(new StringField("accession", "S-DOC1", Field.Store.YES));

      Document doc2 = new Document();
      doc2.add(new StringField("accession", "S-DOC2", Field.Store.YES));

      PaginatedResult<Document> docResult =
          new PaginatedResult<>(List.of(doc1, doc2), 1, 2, 2, true);

      // Act
      PaginatedResult<String> accessionResult = docResult.map(doc -> doc.get("accession"));

      // Assert
      assertEquals(2, accessionResult.results().size());
      assertEquals("S-DOC1", accessionResult.results().get(0));
      assertEquals("S-DOC2", accessionResult.results().get(1));
      assertEquals(1, accessionResult.page());
      assertEquals(2, accessionResult.pageSize());
      assertEquals(2, accessionResult.totalHits());
    }
  }

}
