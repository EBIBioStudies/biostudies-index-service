package uk.ac.ebi.biostudies.index_service.index;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.ac.ebi.biostudies.index_service.analysis.analyzers.AttributeFieldAnalyzer;

/**
 * Configuration component responsible for managing Lucene index settings including:
 * <ul>
 *   <li>Base directory paths for physical index storage</li>
 *   <li>Stop words for text analysis</li>
 *   <li>Document type filtering rules</li>
 * </ul>
 */
@Slf4j
@Component
public class LuceneIndexConfig {

  /**
   * Base directory path where Lucene indices are physically located.
   */
  @Value("${index.base-dir}")
  private String baseDir;

  /**
   * Comma-separated list of stop words to ignore during indexing and search.
   */
  @Value("${indexer.stopwords}")
  private String stopWords;

  /**
   * Query string defining document types to exclude from search results.
   * Format: "type:value1 type:value2 ..."
   * Example: "type:collection type:compound type:array type:file"
   */
  @Value("${indexer.excluded-document-types:}")
  private String excludedDocumentTypes;

  /**
   * Parsed set of stop words for efficient lookup during analysis.
   */
  @Getter
  private CharArraySet stopWordsCache;

  /**
   * Parsed Lucene query for filtering out excluded document types.
   * Used in search queries to exclude internal/system document types.
   */
  @Getter
  private Query typeFilterQuery;

  /**
   * Initializes cached values after properties are injected.
   * Parses stop words and type filter query string.
   */
  @PostConstruct
  public void init() {
    initStopWords();
    initTypeFilterQuery();
  }

  /**
   * Parses and caches stop words from configuration.
   */
  private void initStopWords() {
    if (stopWords != null && !stopWords.isEmpty()) {
      String[] wordsArray = stopWords.split(",");
      stopWordsCache = new CharArraySet(Arrays.asList(wordsArray), true);
      log.info("Initialized stop words cache with {} words", wordsArray.length);
    } else {
      stopWordsCache = CharArraySet.EMPTY_SET;
      log.warn("No stop words configured");
    }
  }

  /**
   * Parses type filter query from configuration.
   * Creates a Lucene query to exclude specified document types from search results.
   */
  private void initTypeFilterQuery() {
    if (!StringUtils.hasText(excludedDocumentTypes)) {
      log.info("No excluded document types configured - type filtering disabled");
      typeFilterQuery = null;
      return;
    }

    try {
      QueryParser parser = new QueryParser("type", new StandardAnalyzer());
      parser.setSplitOnWhitespace(true);
      typeFilterQuery = parser.parse(excludedDocumentTypes);

      log.info("Type filter initialized: {} (excludes: {})",
          typeFilterQuery, excludedDocumentTypes);
    } catch (ParseException e) {
      log.error("Failed to parse excluded document types: {}", excludedDocumentTypes, e);
      throw new IllegalStateException(
          "Invalid type filter configuration: " + excludedDocumentTypes, e);
    }
  }

  /**
   * Returns the full file system path to an individual Lucene index directory.
   *
   * @param indexName the index to get the path for
   * @return the full directory path
   */
  public String getIndexPath(IndexName indexName) {
    return buildPath(indexName.getIndexName());
  }

  /**
   * Constructs the full index directory path by combining base directory with index name.
   *
   * @param indexName the name of the index directory
   * @return the combined directory path
   */
  private String buildPath(String indexName) {
    return baseDir.endsWith("/") ? baseDir + indexName : baseDir + "/" + indexName;
  }
}
