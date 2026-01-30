package uk.ac.ebi.biostudies.index_service.index.management;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.spell.SuggestMode;
import org.apache.lucene.search.spell.SuggestWord;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.analysis.AnalyzerManager;
import uk.ac.ebi.biostudies.index_service.index.IndexName;

/**
 * Manages Lucene index resources including writers, readers, and searchers. Responsible for opening
 * indexes, configuring writer policies, and maintaining the lifecycle of index components.
 */
@Slf4j
@Component
public class IndexManager {

  private static final double TARGET_MAX_STALE_SEC = 5.0;
  private static final double TARGET_MIN_STALE_SEC = 0.1;
  private final IndexContainer container;
  private final AnalyzerManager analyzerManager;
  private final Map<String, ControlledRealTimeReopenThread<IndexSearcher>> reopenThreads =
      new ConcurrentHashMap<>();
  @Setter private DirectSpellChecker spellChecker;

  /**
   * Constructs an IndexManager that uses the given IndexContainer for storing index components.
   *
   * @param container the IndexContainer to hold and manage index writers, readers and searchers
   */
  public IndexManager(IndexContainer container, AnalyzerManager analyzerManager) {
    this.container = container;
    this.analyzerManager = analyzerManager;
  }

  /**
   * Opens a Lucene index at the specified path with the given index name. Opens an IndexWriter,
   * DirectoryReader, and IndexSearcher and saves them in the container. Uses a snapshot deletion
   * policy to keep only the last commit.
   *
   * @param indexName the logical name of the index
   * @param indexPath the filesystem path where the index is located
   * @throws IllegalStateException if an IOException occurs during opening the index
   */
  public void openIndex(IndexName indexName, String indexPath) {
    log.info("Opening index {} [{}]", indexName.getIndexName(), indexPath);
    String indexNameStr = indexName.getIndexName();
    try {
      // Open directories
      FSDirectory indexDirectory = openIndexDirectory(indexPath);
      // Create writer configs
      IndexWriterConfig searchIndexWriterConfig = createWriterConfig();
      // Set snapshot deletion policy
      setSnapshotPolicy(
          searchIndexWriterConfig,
          new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy()));
      // Open writers
      IndexWriter writer = new IndexWriter(indexDirectory, searchIndexWriterConfig);

      DirectoryReader reader = DirectoryReader.open(writer);
      IndexSearcher searcher = new IndexSearcher(reader);

      container.setIndexWriter(indexName, writer);
      container.setIndexReader(indexName, reader);
      container.setIndexSearcher(indexName, searcher);

      SearcherManager manager =
          new SearcherManager(
              writer,
              new SearcherFactory() {
                @Override
                public IndexSearcher newSearcher(IndexReader r, IndexReader prev) {
                  return new IndexSearcher(r); // Customize executor if needed
                }
              });
      container.setSearcherManager(indexName, manager);

      ControlledRealTimeReopenThread<IndexSearcher> thread =
          new ControlledRealTimeReopenThread<>(
              writer, manager, TARGET_MAX_STALE_SEC, TARGET_MIN_STALE_SEC);
      thread.setDaemon(true);
      thread.start();
      reopenThreads.put(indexName.getIndexName(), thread);

      log.info("Index {} [{}] open and ready", indexNameStr, indexPath);
    } catch (IOException e) {
      log.error("Error opening index {} [{}]", indexNameStr, indexPath, e);
      throw new IllegalStateException("Failed to open required index: " + indexNameStr, e);
    }
  }

  /**
   * Opens a Lucene FSDirectory at the given filesystem path.
   *
   * @param indexPath the path of the index directory to open
   * @return a FSDirectory instance pointing to the index directory
   * @throws IOException if an error occurs opening the directory
   */
  private FSDirectory openIndexDirectory(String indexPath) throws IOException {
    return FSDirectory.open(Paths.get(indexPath));
  }

  /**
   * Creates a basic IndexWriterConfig with create or append open mode. Intended to be enhanced with
   * a per-field analyzer wrapper in future updates.
   *
   * @return a configured IndexWriterConfig instance
   */
  private IndexWriterConfig createWriterConfig() {
    IndexWriterConfig config = new IndexWriterConfig(analyzerManager.getPerFieldAnalyzerWrapper());
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    return config;
  }

  /**
   * Sets the snapshot deletion policy on the given IndexWriterConfig. If passed policy is null,
   * assigns a default policy to keep only the last commit.
   *
   * @param config the IndexWriterConfig to set the policy on
   * @param snapshotPolicy the SnapshotDeletionPolicy to use, or null for default
   */
  private void setSnapshotPolicy(IndexWriterConfig config, SnapshotDeletionPolicy snapshotPolicy) {
    if (snapshotPolicy == null) {
      snapshotPolicy = new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
    }
    config.setIndexDeletionPolicy(snapshotPolicy);
  }

  public IndexWriter getPageTabIndexWriter() {
    return container.getIndexWriter(IndexName.PAGE_TAB);
  }

  public IndexWriter getSubmissionIndexWriter() {
    return container.getIndexWriter(IndexName.SUBMISSION);
  }

  public IndexSearcher acquireSubmissionIndexSearcher() throws IOException {
    return container.acquireSearcher(IndexName.SUBMISSION);
  }

  public IndexSearcher acquireSearcher(IndexName indexName) throws IOException {
    return container.acquireSearcher(indexName);
  }

  public void releaseSearcher(IndexName indexName, IndexSearcher searcher) throws IOException {
    container.releaseSearcher(indexName, searcher);
  }

  public IndexWriter getEFOIndexWriter() {
    return container.getIndexWriter(IndexName.EFO);
  }

  public IndexWriter getFilesIndexWriter() {
    return container.getIndexWriter(IndexName.FILES);
  }

  public void refreshAll() {
    container
        .getAllSearcherManagers()
        .values()
        .forEach(
            sm -> {
              try {
                sm.maybeRefreshBlocking();
              } catch (IOException e) {
                log.error("Refresh failed", e);
              }
            });
    // Refresh taxonomy: taxonomyManager.refresh() or similar
  }

  @PreDestroy
  public void closeAll() {
    log.info("Closing all Lucene index resources...");
    reopenThreads.values().forEach(ControlledRealTimeReopenThread::close);
    container
        .getAllSearcherManagers()
        .values()
        .forEach(
            sm -> {
              try {
                sm.close();
              } catch (IOException e) {
                log.error("Close failed", e);
              }
            });
    // Close writers, taxonomy, etc.
  }

  public void commitSubmissionRelatedIndices() throws IOException {
    container.getIndexWriter(IndexName.SUBMISSION).commit();
    container.getIndexWriter(IndexName.FILES).commit();
    container.getIndexWriter(IndexName.PAGE_TAB).commit();
  }

  /**
   * Checks if the spell checker has been initialized.
   *
   * @return true if spell checker is available
   */
  public boolean hasSpellChecker() {
    return spellChecker != null;
  }

  /**
   * Suggests similar terms for the given query string using the spell checker.
   *
   * <p>This method queries the submission index's content field for spelling suggestions. Uses the
   * SearcherManager to acquire/release searchers properly for thread-safe access.
   *
   * @param queryString the query string to get suggestions for
   * @param maxSuggestions maximum number of suggestions to return (typically 5)
   * @return array of suggested spellings, empty array if no suggestions
   * @throws IllegalStateException if spell checker not initialized
   */
  public String[] suggestSimilar(String queryString, int maxSuggestions) {
    if (spellChecker == null) {
      throw new IllegalStateException("Spell checker not initialized");
    }

    IndexSearcher searcher = null;
    try {
      // Acquire searcher through SearcherManager for thread-safe access
      searcher = acquireSearcher(IndexName.SUBMISSION);
      IndexReader reader = searcher.getIndexReader();
      Term term = new Term(Constants.CONTENT, queryString);

      // Get suggestions from the spell checker
      SuggestWord[] suggestions =
          spellChecker.suggestSimilar(
              term, maxSuggestions, reader, SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);

      // Convert SuggestWord[] to String[]
      String[] results = new String[suggestions.length];
      for (int i = 0; i < suggestions.length; i++) {
        results[i] = suggestions[i].string;
        log.debug(
            "Suggestion: '{}' (score: {}, freq: {})",
            suggestions[i].string,
            suggestions[i].score,
            suggestions[i].freq);
      }

      return results;

    } catch (IOException e) {
      log.error("Error getting spelling suggestions for '{}'", queryString, e);
      return new String[0]; // Return empty array on error
    } finally {
      // Always release the searcher
      if (searcher != null) {
        try {
          releaseSearcher(IndexName.SUBMISSION, searcher);
        } catch (IOException e) {
          log.error("Error releasing searcher after spell check", e);
        }
      }
    }
  }
}
