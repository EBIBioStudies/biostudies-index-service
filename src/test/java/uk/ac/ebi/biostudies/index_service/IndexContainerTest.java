package uk.ac.ebi.biostudies.index_service;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexContainer;

class IndexContainerTest {

  private final IndexName testIndexName = IndexName.SUBMISSION; // Example enum value
  private IndexContainer indexContainer;
  // Mock or dummy Lucene objects for testing
  private IndexWriter mockWriter;
  private IndexReader mockReader;
  private IndexSearcher mockSearcher;

  @BeforeEach
  void setUp() {
    indexContainer = new IndexContainer();

    mockWriter = Mockito.mock(IndexWriter.class);
    mockReader = Mockito.mock(DirectoryReader.class); // Mock concrete subclass
    mockSearcher = Mockito.mock(IndexSearcher.class);
  }

  @Test
  void testSetAndGetIndexWriter() {
    indexContainer.setIndexWriter(testIndexName, mockWriter);
    IndexWriter retrievedWriter = indexContainer.getIndexWriter(testIndexName);
    assertSame(
        mockWriter, retrievedWriter, "The retrieved IndexWriter should match the stored instance");
  }

  @Test
  void testGetIndexWriter_NotFound() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> {
              indexContainer.getIndexWriter(testIndexName);
            });
    assertTrue(ex.getMessage().contains(testIndexName.getIndexName()));
  }
}
