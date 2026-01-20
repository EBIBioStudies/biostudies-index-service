package uk.ac.ebi.biostudies.index_service.index.management.init;

import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexInitializer;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

public class StandardIndexInitializer implements IndexInitializer {

  private final IndexName indexName;
  private final String indexPath;
  private final IndexManager indexManager;

  public StandardIndexInitializer(
      IndexName indexName, String indexPath, IndexManager indexManager) {
    this.indexName = indexName;
    this.indexPath = indexPath;
    this.indexManager = indexManager;
  }

  /**
   * Opens the Lucene index, including its directory and any necessary resources. Implementations
   * should ensure the index is ready for read/write operations.
   *
   * @throws IllegalStateException if the index cannot be open
   */
  @Override
  public void open() {
    indexManager.openIndex(indexName, indexPath);
  }
}
