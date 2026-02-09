package uk.ac.ebi.biostudies.index_service.index.efo;

import java.io.IOException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Getter
public class EFOManager {
  private final EFOLoader loader;
  private final EFOIndexer indexer;

  private EFOTermResolver efoTermResolver = null;

  public EFOManager(EFOLoader loader, EFOIndexer indexer) {
    this.loader = loader;
    this.indexer = indexer;
  }

  /**
   * Checks if the EFO index exists in the given directory.
   *
   * @param directory the index directory to check
   * @return true if index exists and is valid, false otherwise
   */
  public boolean isIndexReady(Directory directory) {
    try {
      return DirectoryReader.indexExists(directory);
    } catch (IOException e) {
      log.warn("Error checking if EFO index exists", e);
      return false;
    }
  }

  /**
   * Loads and indexes EFO if the index doesn't exist. This is the equivalent of the legacy
   * createEfoIndex() method.
   *
   * @param directory the index directory to check and populate
   * @throws IOException if loading or indexing fails
   */
  public void initializeIndexIfNeeded(Directory directory) throws IOException {
    if (!isIndexReady(directory)) {
      log.info("EFO index does not exist, creating it now");
      loadEFO();
      indexEFO();
      log.info("EFO index created successfully");
    } else {
      log.info("EFO index already exists, skipping creation");
    }
  }

  /**
   * Loads the EFO model from the efo.owl file. This puts the whole ontology content in memory so
   * the system can index it later.
   */
  public void loadEFO() {
    log.info("Loading EFO ontology from file");
    efoTermResolver = loader.getResolver();
    EFOModel model = efoTermResolver.getModel();
    log.info("EFO ontology loaded: {} nodes", model.getNodeCount());
  }

  /**
   * Updates the EFO index with the content of the efo.owl.
   *
   * @throws IOException if indexing fails
   */
  public void indexEFO() throws IOException {
    if (efoTermResolver == null) {
      throw new IllegalStateException("EFO model not loaded. Call loadEFO() first.");
    }
    log.info("Indexing EFO ontology");
    indexer.indexEFO(efoTermResolver);
    log.info("EFO indexing complete");
  }
}
