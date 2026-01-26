package uk.ac.ebi.biostudies.index_service.index.management.init;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexWriter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOManager;
import uk.ac.ebi.biostudies.index_service.index.management.IndexInitializer;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

// On application initialization, this class checks that a valid EFO index exists and initializes it
// if necessary.
@Slf4j
@Component
public class EfoIndexInitializer implements IndexInitializer {
  private final IndexManager indexManager;
  private final LuceneIndexConfig config;
  private final EFOManager efoManager;

  public EfoIndexInitializer(
      IndexManager indexManager, LuceneIndexConfig config, EFOManager efoManager) {
    this.indexManager = indexManager;
    this.config = config;
    this.efoManager = efoManager;
  }

  /**
   * Opens the Lucene index, including its directory and any necessary resources. Implementations
   * should ensure the index is ready for read/write operations.
   *
   * @throws IllegalStateException if an I/O error occurs while opening the index
   */
  @Override
  public void open() {
    indexManager.openIndex(IndexName.EFO, config.getIndexPath(IndexName.EFO));
  }

  /**
   * Performs any special initialization logic needed for the index, such as creating it if missing
   * and populating from a data source.
   *
   * <p>Equivalent to the legacy createEfoIndex() method: - Checks if EFO index exists - If not,
   * loads EFO ontology and builds index
   *
   * @throws IllegalStateException if an error occurs during initialization
   */
  @Override
  public void initializeIfNeeded() {
    try {
      log.info("Checking EFO index status");

      // Get the writer which has access to the directory
      IndexWriter writer = indexManager.getEFOIndexWriter();

      if (writer == null) {
        log.warn("EFO writer is null, opening index");
        open();
        writer = indexManager.getEFOIndexWriter();
      }

      efoManager.initializeIndexIfNeeded(writer.getDirectory());

    } catch (Exception e) {
      log.error("Problem initializing EFO index", e);
      throw new IllegalStateException("Failed to initialize EFO index", e);
    }
  }
}
