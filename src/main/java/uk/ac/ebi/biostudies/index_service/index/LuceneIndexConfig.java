package uk.ac.ebi.biostudies.index_service.index;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import lombok.Getter;
import org.apache.lucene.analysis.CharArraySet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration component responsible for managing base directory paths for Lucene indexes in the
 * application.
 *
 * <p>The base directory path is injected from the application properties via the 'baseDir'
 * property. This class provides utility methods to build the full physical path for individual
 * Lucene indexes.
 */
@Component
public class LuceneIndexConfig {

  /**
   * Base directory path where Lucene indices are physically located. This is loaded from the
   * application properties 'baseDir'.
   */
  @Value("${index.base-dir}")
  private String baseDir;

  /**
   * List of comma-separated words that will be ignored in the indexing process
   */
  @Value("${indexer.stopwords}")
  private String stopWords;

  /**
   * Parsed set of values based on {@code stopWords}. It avoids parsing again the string each time
   * the value is required.
   */
  @Getter
  private CharArraySet stopWordsCache;

  @PostConstruct
  public void init() {
    if (stopWords != null && !stopWords.isEmpty()) {
      String[] wordsArray = stopWords.split(",");
      stopWordsCache = new CharArraySet(Arrays.asList(wordsArray), true);
    } else {
      stopWordsCache = CharArraySet.EMPTY_SET;
    }
  }

  /**
   * Returns the full file system path to an individual Lucene index directory, resolved by
   * appending the given index name to the configured base directory.
   *
   * @param indexName the {@link IndexName} representing the specific Lucene index
   * @return the full directory path as a {@link String}
   */
  public String getIndexPath(IndexName indexName) {
    return buildPath(indexName.getIndexName());
  }

  /**
   * Helper method to construct the full index directory path by combining the base directory with
   * the specific index name.
   *
   * <p>This method ensures proper path separator handling for consistent cross-platform
   * compatibility.
   *
   * @param indexName the name of the index directory to append
   * @return the combined directory path as a {@link String}
   */
  private String buildPath(String indexName) {
    // Ensure consistent path separator usage, handle defaults as necessary
    return baseDir.endsWith("/") ? baseDir + indexName : baseDir + "/" + indexName;
  }
}
