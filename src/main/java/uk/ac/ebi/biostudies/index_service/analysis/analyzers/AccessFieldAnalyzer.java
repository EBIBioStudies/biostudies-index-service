package uk.ac.ebi.biostudies.index_service.analysis.analyzers;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.util.CharTokenizer;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;

/**
 * Analyzer implementation for processing text in the "access" field of a Lucene index.
 *
 * <p>This analyzer is designed to handle identifiers such as usernames, email-like values, and
 * other structured tokens that may contain symbols like '@', '.', '-', or '_'. It tokenizes text
 * by separating non-allowed characters, performs ASCII folding to normalize accented characters,
 * removes stop words defined in the {@link LuceneIndexConfig}, and converts all text to lowercase
 * for case-insensitive matching.</p>
 *
 * <p>The normalization step ensures that query input is lowercased to match the indexed tokens.</p>
 */
@Component("AccessFieldAnalyzer")
public final class AccessFieldAnalyzer extends Analyzer {

  private final LuceneIndexConfig config;

  /**
   * Constructs an AccessFieldAnalyzer with a given Lucene index configuration.
   *
   * @param config configuration containing stop word definitions for filtering
   */
  public AccessFieldAnalyzer(LuceneIndexConfig config) {
    this.config = config;
  }

  /**
   * Builds the tokenization and filtering pipeline for this analyzer.
   *
   * <p>Pipeline:</p>
   * <ol>
   *   <li>Custom tokenizer allowing alphanumeric characters and specific symbols (e.g., @ . ~ # - _)</li>
   *   <li>Applies ASCII folding to normalize diacritics</li>
   *   <li>Removes stopwords from the {@code config.getStopWordsCache()}</li>
   *   <li>Converts tokens to lowercase</li>
   * </ol>
   *
   * @param fieldName the field being analyzed
   * @return TokenStreamComponents describing the analyzer chain
   */
  @Override
  protected TokenStreamComponents createComponents(String fieldName) {
    Tokenizer source = new AccessFieldAnalyzerTextTokenizer();
    TokenStream filter = new ASCIIFoldingFilter(source);
    filter = new StopFilter(filter, config.getStopWordsCache());
    filter = new LowerCaseFilter(filter);
    return new TokenStreamComponents(source, filter);
  }

  /**
   * Normalization step applied during query parsing or reindexing,
   * converting input text to lowercase for consistent comparison.
   *
   * @param fieldName name of the field to normalize
   * @param in the input token stream
   * @return normalized token stream
   */
  @Override
  protected TokenStream normalize(String fieldName, TokenStream in) {
    return new LowerCaseFilter(in);
  }

  /**
   * Tokenizer that defines which characters are treated as part of a token.
   *
   * <p>Allowed characters include:</p>
   * <ul>
   *   <li>Letters (A–Z, a–z)</li>
   *   <li>Digits (0–9)</li>
   *   <li>Symbols: @ . ~ # - _</li>
   * </ul>
   */
  private static class AccessFieldAnalyzerTextTokenizer extends CharTokenizer {
    @Override
    protected boolean isTokenChar(int c) {
      return Character.isLetter(c)
          || Character.isDigit(c)
          || c == '@'
          || c == '.'
          || c == '~'
          || c == '#'
          || c == '-'
          || c == '_';
    }
  }
}