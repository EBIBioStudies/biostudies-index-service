package uk.ac.ebi.biostudies.index_service.index.management;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;
import uk.ac.ebi.biostudies.index_service.index.management.init.EfoIndexInitializer;
import uk.ac.ebi.biostudies.index_service.index.management.init.SpellCheckerInitializer;
import uk.ac.ebi.biostudies.index_service.index.management.init.StandardIndexInitializer;

/**
 * Factory component responsible for initializing and opening Lucene indices in the application.
 *
 * <p>This Spring-managed {@code @Component} aggregates and manages multiple {@link
 * IndexInitializer} implementations that handle setup tasks for standard and special indices.
 *
 * <p>The factory provides methods to open all standard indexes (like the submission index) and to
 * run special initialization logic for indices such as the EFO index and spell checker, including
 * directory creation, index writer/reader preparation, and conditional creation of missing indexes.
 *
 * <p>It centralizes index initialization logic and simplifies orchestration of Lucene resource
 * setup by exposing clear, high-level methods.
 *
 * <p><strong>Initialization Order:</strong>
 *
 * <ol>
 *   <li>Standard indexes (submission, files, page tab) - {@link #openStandardIndexes()}
 *   <li>EFO index - {@link #initializeEfoIndex()}
 *   <li>Spell checker - {@link #initializeSpellChecker()} (depends on submission index)
 * </ol>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Autowired
 * private IndexInitializerFactory indexInitializerFactory;
 *
 * // Initialize all indexes in correct order
 * indexInitializerFactory.initializeAllIndexes();
 *
 * // Or initialize individually
 * indexInitializerFactory.openStandardIndexes();
 * indexInitializerFactory.initializeEfoIndex();
 * indexInitializerFactory.initializeSpellChecker();
 * }</pre>
 */
@Component
public class IndexInitializerFactory {
  private final LuceneIndexConfig luceneIndexConfig;
  private final List<IndexInitializer> standardIndexInitializers;
  private final EfoIndexInitializer efoIndexInitializer;
  private final SpellCheckerInitializer spellCheckerInitializer;
  private final IndexManager indexManager;

  /**
   * Constructs the factory with required dependencies.
   *
   * @param luceneIndexConfig configuration for index paths
   * @param efoIndexInitializer initializer for the EFO ontology index
   * @param spellCheckerInitializer initializer for the spell checker
   * @param indexManager the index manager for storing index resources
   */
  public IndexInitializerFactory(
      LuceneIndexConfig luceneIndexConfig,
      EfoIndexInitializer efoIndexInitializer,
      SpellCheckerInitializer spellCheckerInitializer,
      IndexManager indexManager) {
    this.luceneIndexConfig = luceneIndexConfig;
    this.efoIndexInitializer = efoIndexInitializer;
    this.spellCheckerInitializer = spellCheckerInitializer;
    this.indexManager = indexManager;
    this.standardIndexInitializers =
        Arrays.asList(
            buildSubmissionIndexInitializer(),
            buildFilesIndexInitializer(),
            buildPageTabIndexInitializer());
  }

  /**
   * Opens all standard Lucene indexes (directories, writers, readers, searchers).
   *
   * <p>Standard indexes include:
   *
   * <ul>
   *   <li>Submission index - main search index for submissions
   *   <li>Files index - index for file metadata
   *   <li>Page tab index - index for page tab data
   * </ul>
   */
  public void openStandardIndexes() {
    for (IndexInitializer initializer : standardIndexInitializers) {
      initializer.open();
    }
  }

  /**
   * Runs special initialization for the EFO index.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Opens the EFO index directory and resources
   *   <li>Builds the EFO index if it doesn't exist or is outdated
   * </ol>
   *
   * <p>The EFO index is used for ontology-based query expansion.
   */
  public void initializeEfoIndex() {
    efoIndexInitializer.open();
    efoIndexInitializer.initializeIfNeeded();
  }

  /**
   * Initializes the spell checker for query spelling suggestions.
   *
   * <p>The spell checker uses {@link org.apache.lucene.search.spell.DirectSpellChecker} which works
   * directly on the submission index without requiring a separate index. This method can be called
   * even before documents are indexed (suggestions will be empty until documents exist).
   *
   * <p><strong>Prerequisite:</strong> The submission index should be opened via {@link
   * #openStandardIndexes()} before calling this method.
   */
  public void initializeSpellChecker() {
    spellCheckerInitializer.initialize();
  }

  /**
   * Initializes all indexes in the correct dependency order.
   *
   * <p>This is a convenience method that calls:
   *
   * <ol>
   *   <li>{@link #openStandardIndexes()} - Standard indexes
   *   <li>{@link #initializeEfoIndex()} - EFO ontology index
   *   <li>{@link #initializeSpellChecker()} - Spell checker (depends on submission index)
   * </ol>
   *
   * <p>Use this method during application startup to initialize all indexing resources at once.
   */
  public void initializeAllIndexes() {
    openStandardIndexes();
    initializeEfoIndex();
    initializeSpellChecker();
  }

  /**
   * Builds the submission index initializer.
   *
   * @return configured initializer for the submission index
   */
  private StandardIndexInitializer buildSubmissionIndexInitializer() {
    return new StandardIndexInitializer(
        IndexName.SUBMISSION, luceneIndexConfig.getIndexPath(IndexName.SUBMISSION), indexManager);
  }

  /**
   * Builds the files index initializer.
   *
   * @return configured initializer for the files index
   */
  private StandardIndexInitializer buildFilesIndexInitializer() {
    return new StandardIndexInitializer(
        IndexName.FILES, luceneIndexConfig.getIndexPath(IndexName.FILES), indexManager);
  }

  /**
   * Builds the page tab index initializer.
   *
   * @return configured initializer for the page tab index
   */
  private StandardIndexInitializer buildPageTabIndexInitializer() {
    return new StandardIndexInitializer(
        IndexName.PAGE_TAB, luceneIndexConfig.getIndexPath(IndexName.PAGE_TAB), indexManager);
  }
}
