package uk.ac.ebi.biostudies.index_service.search.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
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
    @DisplayName("Should execute query and return paginated results with SearchHits")
    void shouldExecuteQueryAndReturnPaginatedResults() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      int page = 1;
      int pageSize = 10;

      ScoreDoc[] scoreDocs = new ScoreDoc[10];
      for (int i = 0; i < 10; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(100, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 10)).thenReturn(topDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      // Mock documents with required fields
      for (int i = 0; i < 10; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, page, pageSize);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.page());
      assertEquals(10, result.pageSize());
      assertEquals(100, result.totalHits());
      assertEquals(10, result.hits().size());
      assertTrue(result.isTotalHitsExact());

      // Verify first hit
      assertEquals("S-ACC0", result.hits().get(0).accession());
      assertEquals("study", result.hits().get(0).type());

      verify(indexManager).acquireSearcher(IndexName.SUBMISSION);
      verify(indexManager).releaseSearcher(IndexName.SUBMISSION, mockSearcher);
    }

    @Test
    @DisplayName("Should execute query with sorting")
    void shouldExecuteQueryWithSorting() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      Sort sort = new Sort(new SortField("title", SortField.Type.STRING));

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
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 1, 20, sort);

      // Assert
      assertNotNull(result);
      assertEquals(50, result.totalHits());
      assertEquals(5, result.hits().size());
      verify(mockSearcher).search(query, 20, sort);
    }

    @Test
    @DisplayName("Should handle page 2 correctly - only return current page hits")
    void shouldHandleSecondPageCorrectly() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      int page = 2;
      int pageSize = 20;

      ScoreDoc[] scoreDocs = new ScoreDoc[40];
      for (int i = 0; i < 40; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(100, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 40)).thenReturn(topDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      // Mock documents for page 2 only (indices 20-39)
      for (int i = 20; i < 40; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, page, pageSize);

      // Assert
      assertEquals(2, result.page());
      assertEquals(20, result.pageSize());
      assertEquals(20, result.hits().size()); // Should have 20 hits for page 2
      assertEquals("S-ACC20", result.hits().get(0).accession()); // First doc of page 2
      assertEquals("S-ACC39", result.hits().get(19).accession()); // Last doc of page 2
      verify(mockSearcher).search(query, 40);
    }

    @Test
    @DisplayName("Should limit page size to MAX_PAGE_SIZE")
    void shouldLimitPageSizeToMaxPageSize() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      int excessivePageSize = 5000; // Exceeds MAX_PAGE_SIZE of 1000

      ScoreDoc[] scoreDocs = new ScoreDoc[1000];
      for (int i = 0; i < 1000; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(5000, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(eq(query), anyInt())).thenReturn(topDocs);
      when(mockSearcher.storedFields()).thenReturn(mockStoredFields);

      for (int i = 0; i < 1000; i++) {
        Document doc = createMockDocument("S-ACC" + i, "study", "Title " + i);
        when(mockStoredFields.document(i)).thenReturn(doc);
      }

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 1, excessivePageSize);

      // Assert
      assertEquals(1000, result.pageSize()); // Limited to MAX_PAGE_SIZE
      assertEquals(1000, result.hits().size());
      verify(mockSearcher).search(query, 1000);
    }

    @Test
    @DisplayName("Should always release searcher even when exception occurs")
    void shouldAlwaysReleaseSearcherOnException() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(any(Query.class), anyInt()))
          .thenThrow(new IOException("Search failed"));

      // Act & Assert
      assertThrows(IOException.class, () -> executor.execute(IndexName.SUBMISSION, query, 1, 10));

      verify(indexManager).releaseSearcher(IndexName.SUBMISSION, mockSearcher);
    }

    @Test
    @DisplayName("Should handle estimate total hits")
    void shouldHandleEstimateTotalHits() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();

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
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 1, 10);

      // Assert
      assertFalse(result.isTotalHitsExact());
      assertEquals(1000, result.totalHits());
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
  @DisplayName("Integration Tests with RAMDirectory")
  class IntegrationTests {

    private ByteBuffersDirectory directory;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    @BeforeEach
    void setUpIndex() throws IOException {
      // Create in-memory directory and writer
      directory = new ByteBuffersDirectory();
      IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
      writer = new IndexWriter(directory, config);

      // Add test documents with all required fields
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

      // Open reader and searcher
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

      // Act - Page 1
      PaginatedResult page1 = executor.execute(IndexName.SUBMISSION, query, 1, pageSize);

      // Assert Page 1
      assertEquals(1, page1.page());
      assertEquals(10, page1.pageSize());
      assertEquals(50, page1.totalHits());
      assertTrue(page1.isTotalHitsExact());
      assertEquals(10, page1.hits().size());
      assertEquals("S-DOC1", page1.hits().get(0).accession());

      // Act - Page 2
      PaginatedResult page2 = executor.execute(IndexName.SUBMISSION, query, 2, pageSize);

      // Assert Page 2
      assertEquals(2, page2.page());
      assertEquals(10, page2.hits().size());

      // Act - Page 5 (last page)
      PaginatedResult page5 = executor.execute(IndexName.SUBMISSION, query, 5, pageSize);

      // Assert Page 5
      assertEquals(5, page5.page());
      assertEquals(10, page5.hits().size());
    }

    @Test
    @DisplayName("Should return correct documents for specific term query")
    void shouldReturnCorrectDocumentsForTermQuery() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new TermQuery(new Term("category", "java"));

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 1, 10);

      // Assert
      assertEquals(3, result.totalHits()); // S-DOC1, S-DOC3, S-DOC5 have category "java"
      assertEquals(3, result.hits().size());
      assertTrue(result.hits().stream().anyMatch(h -> h.accession().equals("S-DOC1")));
      assertTrue(result.hits().stream().anyMatch(h -> h.accession().equals("S-DOC3")));
      assertTrue(result.hits().stream().anyMatch(h -> h.accession().equals("S-DOC5")));
    }

    @Test
    @DisplayName("Should sort results by timestamp descending")
    void shouldSortResultsByTimestampDescending() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new TermQuery(new Term("category", "java"));
      Sort sort = new Sort(new SortField("timestamp", SortField.Type.LONG, true)); // descending

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 1, 10, sort);

      // Assert
      assertEquals(3, result.totalHits());
      assertEquals(3, result.hits().size());

      // Verify order: S-DOC5 (250), S-DOC3 (150), S-DOC1 (100)
      assertEquals("S-DOC5", result.hits().get(0).accession());
      assertEquals("S-DOC3", result.hits().get(1).accession());
      assertEquals("S-DOC1", result.hits().get(2).accession());
    }

    @Test
    @DisplayName("Should handle empty page correctly")
    void shouldHandleEmptyPageCorrectly() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();

      // Act - Request page 10 with pageSize 10 (but only 50 docs exist)
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 10, 10);

      // Assert
      assertEquals(10, result.page());
      assertEquals(50, result.totalHits());
      assertEquals(0, result.hits().size()); // No docs on this page
    }

    @Test
    @DisplayName("Should handle partial last page correctly")
    void shouldHandlePartialLastPageCorrectly() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();

      // Act - Page 4 with pageSize 15 (should have only 5 docs: 46-50)
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 4, 15);

      // Assert
      assertEquals(4, result.page());
      assertEquals(50, result.totalHits());
      assertEquals(5, result.hits().size()); // Only 5 docs remaining (46-50)
    }

    @Test
    @DisplayName("Should work with different index names")
    void shouldWorkWithDifferentIndexNames() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.FILES)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();

      // Act
      PaginatedResult result = executor.execute(IndexName.FILES, query, 1, 10);

      // Assert
      assertNotNull(result);
      assertEquals(10, result.hits().size());
      verify(indexManager).acquireSearcher(IndexName.FILES);
      verify(indexManager).releaseSearcher(IndexName.FILES, searcher);
    }
  }

  @Nested
  @DisplayName("PaginatedResult Helper Methods Tests")
  class PaginatedResultTests {

    @Test
    @DisplayName("Should calculate total pages correctly")
    void shouldCalculateTotalPagesCorrectly() {
      // Arrange & Act
      PaginatedResult result1 = new PaginatedResult(java.util.List.of(), 1, 20, 100, true);
      PaginatedResult result2 = new PaginatedResult(java.util.List.of(), 1, 20, 95, true);
      PaginatedResult result3 = new PaginatedResult(java.util.List.of(), 1, 10, 53, true);

      // Assert
      assertEquals(5, result1.getTotalPages()); // 100 / 20 = 5
      assertEquals(5, result2.getTotalPages()); // 95 / 20 = 4.75 -> 5
      assertEquals(6, result3.getTotalPages()); // 53 / 10 = 5.3 -> 6
    }

    @Test
    @DisplayName("Should correctly identify if next page exists")
    void shouldIdentifyNextPageExists() {
      // Arrange & Act
      PaginatedResult page1 = new PaginatedResult(java.util.List.of(), 1, 20, 100, true);
      PaginatedResult page4 = new PaginatedResult(java.util.List.of(), 4, 20, 100, true);
      PaginatedResult lastPage = new PaginatedResult(java.util.List.of(), 5, 20, 100, true);
      PaginatedResult beyondLast = new PaginatedResult(java.util.List.of(), 6, 20, 100, true);

      // Assert
      assertTrue(page1.hasNextPage());   // page 1 of 5 - has next
      assertTrue(page4.hasNextPage());   // page 4 of 5 - has next
      assertFalse(lastPage.hasNextPage()); // page 5 of 5 - no next
      assertFalse(beyondLast.hasNextPage()); // page 6 of 5 - beyond last
    }


    @Test
    @DisplayName("Should correctly identify if previous page exists")
    void shouldIdentifyPreviousPageExists() {
      // Arrange & Act
      PaginatedResult page1 = new PaginatedResult(java.util.List.of(), 1, 20, 100, true);
      PaginatedResult page2 = new PaginatedResult(java.util.List.of(), 2, 20, 100, true);
      PaginatedResult page5 = new PaginatedResult(java.util.List.of(), 5, 20, 100, true);

      // Assert
      assertFalse(page1.hasPreviousPage());
      assertTrue(page2.hasPreviousPage());
      assertTrue(page5.hasPreviousPage());
    }
  }
}
