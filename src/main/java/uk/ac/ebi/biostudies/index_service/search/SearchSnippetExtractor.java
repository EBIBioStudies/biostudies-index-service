package uk.ac.ebi.biostudies.index_service.search;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.NullFragmenter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.analysis.analyzers.ExperimentTextAnalyzer;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;

/**
 * Extracts relevant text snippets from search result documents based on query matches.
 *
 * <p>This component uses Lucene's highlighting API to identify the most relevant portions of text
 * that contain query matches and extract contextual snippets for display in search results. The
 * snippets help users understand why a document matched their query without reading the entire
 * content.
 *
 * <p>Snippet extraction can operate in two modes:
 *
 * <ul>
 *   <li><b>Fragment mode</b>: Extracts a fixed-size snippet around the best matching section
 *   <li><b>Full text mode</b>: Returns the entire text content without truncation
 * </ul>
 */
@Slf4j
@Component
public class SearchSnippetExtractor {

  private final LuceneIndexConfig indexConfig;

  public SearchSnippetExtractor(LuceneIndexConfig indexConfig) {
    this.indexConfig = indexConfig;
  }

  /**
   * Extracts a relevant text snippet from the given text based on the query.
   *
   * <p>This method analyzes the text to find the portion that best matches the query and returns
   * either a truncated snippet or the full text depending on the {@code fragmentOnly} parameter.
   *
   * @param query the Lucene query used to identify relevant portions of text
   * @param fieldName the field name to extract from; if empty, uses the default field
   * @param text the full text content to extract a snippet from
   * @param fragmentOnly if true, returns a fixed-size snippet; if false, returns full text
   * @return the extracted snippet, or the original text if extraction fails
   */
  public String extractSnippet(Query query, String fieldName, String text, boolean fragmentOnly) {
    try {
      // Use empty markers since we don't add any visual highlighting
      SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("", "");

      String targetField = fieldName.isEmpty() ? indexConfig.getDefaultField() : fieldName;

      QueryScorer scorer = new QueryScorer(query, fieldName, indexConfig.getDefaultField());
      Highlighter highlighter = new Highlighter(formatter, scorer);

      // Choose fragmenter based on whether we want a snippet or full text
      highlighter.setTextFragmenter(
          fragmentOnly
              ? new SimpleSpanFragmenter(scorer, indexConfig.getSearchSnippetFragmentSize())
              : new NullFragmenter());

      String snippet =
          highlighter.getBestFragment(new ExperimentTextAnalyzer(indexConfig), targetField, text);

      return snippet != null ? snippet : text;

    } catch (InvalidTokenOffsetsException e) {
      log.error("Failed to extract snippet for field '{}': {}", fieldName, e.getMessage(), e);
      return text;
    } catch (Exception e) {
      log.error(
          "Unexpected error during snippet extraction for field '{}': {}",
          fieldName,
          e.getMessage(),
          e);
      return text;
    }
  }

  /**
   * Convenience method to extract a snippet using the default field.
   *
   * @param query the Lucene query used to identify relevant portions of text
   * @param text the full text content to extract a snippet from
   * @param fragmentOnly if true, returns a fixed-size snippet; if false, returns full text
   * @return the extracted snippet, or the original text if extraction fails
   */
  public String extractSnippet(Query query, String text, boolean fragmentOnly) {
    return extractSnippet(query, "", text, fragmentOnly);
  }
}
