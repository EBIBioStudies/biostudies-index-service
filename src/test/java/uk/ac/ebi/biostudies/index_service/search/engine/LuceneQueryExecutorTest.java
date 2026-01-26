package uk.ac.ebi.biostudies.index_service.search.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

  private LuceneQueryExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new LuceneQueryExecutor(indexManager);
  }

  @Nested
  @DisplayName("Unit Tests with Mocks")
  class UnitTests {

    @Test
    @DisplayName("Should execute query and return paginated results")
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

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, page, pageSize);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.page());
      assertEquals(10, result.pageSize());
      assertEquals(100, result.totalHits());
      assertEquals(10, result.topDocs().scoreDocs.length);
      assertTrue(result.isTotalHitsExact());

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
      TopFieldDocs topFieldDocs = new TopFieldDocs(
          new TotalHits(50, TotalHits.Relation.EQUAL_TO),
          scoreDocs,
          new SortField[]{new SortField("title", SortField.Type.STRING)}
      );

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      doReturn(topFieldDocs).when(mockSearcher).search(query, 20, sort);

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 1, 20, sort);

      // Assert
      assertNotNull(result);
      assertEquals(50, result.totalHits());
      verify(mockSearcher).search(query, 20, sort);
    }

    @Test
    @DisplayName("Should handle page 2 correctly")
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
      when(mockSearcher.search(query, 40)).thenReturn(topDocs); // page * pageSize = 2 * 20 = 40

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, page, pageSize);

      // Assert
      assertEquals(2, result.page());
      assertEquals(20, result.pageSize());
      verify(mockSearcher).search(query, 40);
    }

    @Test
    @DisplayName("Should limit page size to MAX_PAGE_SIZE")
    void shouldLimitPageSizeToMaxPageSize() throws IOException {
      // Arrange
      Query query = new MatchAllDocsQuery();
      int excessivePageSize = 5000; // Assuming MAX_PAGE_SIZE is 1000

      ScoreDoc[] scoreDocs = new ScoreDoc[1000];
      for (int i = 0; i < 1000; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(1000, TotalHits.Relation.EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(eq(query), anyInt())).thenReturn(topDocs);

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 1, excessivePageSize);

      // Assert
      assertEquals(1000, result.pageSize()); // Limited to MAX_PAGE_SIZE
      verify(mockSearcher).search(query, 1000); // Should request 1 * 1000 = 1000
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
      assertThrows(
          IOException.class,
          () -> executor.execute(IndexName.SUBMISSION, query, 1, 10));

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
          new TopDocs(
              new TotalHits(1000, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), scoreDocs);

      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(mockSearcher);
      when(mockSearcher.search(query, 10)).thenReturn(topDocs);

      // Act
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 1, 10);

      // Assert
      assertFalse(result.isTotalHitsExact());
      assertEquals(1000, result.totalHits());
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

      // Add test documents
      addDocument("doc1", "Java programming tutorial", "java", 100L);
      addDocument("doc2", "Python programming guide", "python", 200L);
      addDocument("doc3", "Java advanced concepts", "java", 150L);
      addDocument("doc4", "Lucene search engine", "lucene", 300L);
      addDocument("doc5", "Spring framework basics", "java", 250L);
      for (int i = 6; i <= 50; i++) {
        addDocument("doc" + i, "Test document " + i, "test", (long) i * 10);
      }

      writer.commit();
      writer.close();

      // Open reader and searcher
      reader = DirectoryReader.open(directory);
      searcher = new IndexSearcher(reader);
    }

    private void addDocument(String id, String content, String category, Long timestamp)
        throws IOException {
      Document doc = new Document();
      doc.add(new StringField("id", id, Field.Store.YES));
      doc.add(new TextField("content", content, Field.Store.YES));
      doc.add(new StringField("category", category, Field.Store.YES));
      doc.add(new LongPoint("timestamp", timestamp));
      doc.add(new StoredField("timestamp", timestamp));
      doc.add(new NumericDocValuesField("timestamp", timestamp));
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
      assertEquals(0, page1.getStartIndex());
      assertEquals(10, page1.getEndIndex());

      // Act - Page 2
      PaginatedResult page2 = executor.execute(IndexName.SUBMISSION, query, 2, pageSize);

      // Assert Page 2
      assertEquals(2, page2.page());
      assertEquals(10, page2.getStartIndex());
      assertEquals(20, page2.getEndIndex());

      // Act - Page 5 (last page)
      PaginatedResult page5 = executor.execute(IndexName.SUBMISSION, query, 5, pageSize);

      // Assert Page 5
      assertEquals(5, page5.page());
      assertEquals(40, page5.getStartIndex());
      assertEquals(50, page5.getEndIndex());
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
      assertEquals(3, result.totalHits()); // doc1, doc3, doc5 have category "java"
      assertEquals(3, result.topDocs().scoreDocs.length);
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
      ScoreDoc[] docs = result.getCurrentPageDocs();
      assertEquals(3, docs.length);

      // Verify order: doc5 (250), doc3 (150), doc1 (100)
      Document firstDoc = searcher.storedFields().document(docs[0].doc);
      assertEquals("doc5", firstDoc.get("id"));

      Document secondDoc = searcher.storedFields().document(docs[1].doc);
      assertEquals("doc3", secondDoc.get("id"));

      Document thirdDoc = searcher.storedFields().document(docs[2].doc);
      assertEquals("doc1", thirdDoc.get("id"));
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
      assertEquals(0, result.getCurrentPageDocs().length); // No docs on this page
    }

    @Test
    @DisplayName("Should handle partial last page correctly")
    void shouldHandlePartialLastPageCorrectly() throws IOException {
      // Arrange
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(searcher);

      Query query = new MatchAllDocsQuery();

      // Act - Page 5 with pageSize 15 (should have only 5 docs: 45-50)
      PaginatedResult result = executor.execute(IndexName.SUBMISSION, query, 4, 15);

      // Assert
      assertEquals(4, result.page());
      assertEquals(50, result.totalHits());
      assertEquals(5, result.getCurrentPageDocs().length); // Only 5 docs remaining
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
      verify(indexManager).acquireSearcher(IndexName.FILES);
      verify(indexManager).releaseSearcher(IndexName.FILES, searcher);
    }
  }

  @Nested
  @DisplayName("PaginatedResult Helper Methods Tests")
  class PaginatedResultTests {

    @Test
    @DisplayName("Should calculate correct start and end indices")
    void shouldCalculateCorrectIndices() {
      // Arrange
      ScoreDoc[] scoreDocs = new ScoreDoc[100];
      for (int i = 0; i < 100; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(100, TotalHits.Relation.EQUAL_TO), scoreDocs);

      // Act
      PaginatedResult page1 = new PaginatedResult(topDocs, 1, 20, 100);
      PaginatedResult page3 = new PaginatedResult(topDocs, 3, 20, 100);

      // Assert
      assertEquals(0, page1.getStartIndex());
      assertEquals(20, page1.getEndIndex());

      assertEquals(40, page3.getStartIndex());
      assertEquals(60, page3.getEndIndex());
    }

    @Test
    @DisplayName("Should return correct current page docs")
    void shouldReturnCorrectCurrentPageDocs() {
      // Arrange
      ScoreDoc[] scoreDocs = new ScoreDoc[50];
      for (int i = 0; i < 50; i++) {
        scoreDocs[i] = new ScoreDoc(i, (float) i);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(50, TotalHits.Relation.EQUAL_TO), scoreDocs);

      // Act
      PaginatedResult page2 = new PaginatedResult(topDocs, 2, 10, 50);
      ScoreDoc[] currentPageDocs = page2.getCurrentPageDocs();

      // Assert
      assertEquals(10, currentPageDocs.length);
      assertEquals(10, currentPageDocs[0].doc); // First doc of page 2
      assertEquals(19, currentPageDocs[9].doc); // Last doc of page 2
    }

    @Test
    @DisplayName("Should handle empty page gracefully")
    void shouldHandleEmptyPageGracefully() {
      // Arrange
      ScoreDoc[] scoreDocs = new ScoreDoc[10];
      for (int i = 0; i < 10; i++) {
        scoreDocs[i] = new ScoreDoc(i, 1.0f);
      }
      TopDocs topDocs = new TopDocs(new TotalHits(100, TotalHits.Relation.EQUAL_TO), scoreDocs);

      // Act
      PaginatedResult page5 = new PaginatedResult(topDocs, 5, 10, 100);
      ScoreDoc[] currentPageDocs = page5.getCurrentPageDocs();

      // Assert
      assertEquals(0, currentPageDocs.length);
    }
  }
}
