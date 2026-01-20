package uk.ac.ebi.biostudies.index_service.index.management;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.management.init.EfoIndexInitializer;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;
import uk.ac.ebi.biostudies.index_service.index.management.init.StandardIndexInitializer;

/**
 * Factory component responsible for initializing and opening Lucene indices in the application.
 *
 * <p>This Spring-managed {@code @Component} aggregates and manages multiple {@link
 * IndexInitializer} implementations that handle setup tasks for standard and special indices.
 *
 * <p>The factory provides methods to open all standard indexes (like the submission index) and to
 * run special initialization logic for indices such as the EFO index, including directory creation,
 * index writer/reader preparation, and conditional creation of missing indexes.
 *
 * <p>It centralizes index initialization logic and simplifies orchestration of Lucene resource
 * setup by exposing clear, high-level methods.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Autowired
 * private IndexInitializerFactory indexInitializerFactory;
 *
 * indexInitializerFactory.openStandardIndexes();
 * indexInitializerFactory.initializeEfoIndex();
 * }</pre>
 */
@Component
public class IndexInitializerFactory {
  private final LuceneIndexConfig luceneIndexConfig;
  private final List<IndexInitializer> standardIndexInitializers;
  private final EfoIndexInitializer efoIndexInitializer;
  private final IndexContainer indexContainer;
  private final IndexManager indexManager;

  public IndexInitializerFactory(
      LuceneIndexConfig luceneIndexConfig,
      IndexContainer indexContainer,
      IndexManager indexManager) {
    this.luceneIndexConfig = luceneIndexConfig;
    this.indexContainer = indexContainer;
    this.indexManager = indexManager;
    standardIndexInitializers =
        Arrays.asList(
            buildSubmissionIndexInitializer(),
            buildFilesIndexInitializer(),
            buildPageTabIndexInitializer());
    this.efoIndexInitializer = new EfoIndexInitializer();
  }

  /** Opens all standard Lucene indexes (directories, writers, readers). */
  public void openStandardIndexes() {
    for (IndexInitializer initializer : standardIndexInitializers) {
      initializer.open();
    }
  }

  /**
   * Runs special initialization for the EFO index, including creating the index if missing, and
   * dependent updates.
   */
  public void initializeEfoIndex() {
    efoIndexInitializer.open(); // Open EFO index directory etc.
    efoIndexInitializer.initializeIfNeeded(); // Build the special index if required
  }

  private StandardIndexInitializer buildSubmissionIndexInitializer() {
    return new StandardIndexInitializer(
        IndexName.SUBMISSION, luceneIndexConfig.getIndexPath(IndexName.SUBMISSION), indexManager);
  }

  private StandardIndexInitializer buildFilesIndexInitializer() {
    return new StandardIndexInitializer(
        IndexName.FILES, luceneIndexConfig.getIndexPath(IndexName.FILES), indexManager);
  }

  private StandardIndexInitializer buildPageTabIndexInitializer() {
    return new StandardIndexInitializer(
        IndexName.PAGE_TAB, luceneIndexConfig.getIndexPath(IndexName.PAGE_TAB), indexManager);
  }
}
