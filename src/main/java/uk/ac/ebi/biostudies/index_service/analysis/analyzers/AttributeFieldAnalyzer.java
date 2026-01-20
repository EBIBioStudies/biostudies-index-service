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
 * Default Lucene analyzer responsible for tokenizing and filtering attribute field values
 * within the system's search index. This analyzer is automatically used if no other
 * analyzer is explicitly specified in the configuration.
 *
 * <p>
 * It uses a custom {@link AttributeFieldTokenizer} to split text on whitespace and common
 * punctuation characters such as commas, semicolons, and parentheses. The resulting tokens
 * are passed through an {@link ASCIIFoldingFilter} to normalize accented characters,
 * a {@link StopFilter} to remove stopwords (from {@link LuceneIndexConfig}),
 * and a {@link LowerCaseFilter} to ensure case-insensitive matching.
 * </p>
 *
 * <p>
 * The {@link #normalize(String, TokenStream)} method applies lowercase normalization
 * to support consistent indexing and query processing.
 * </p>
 *
 * @see org.apache.lucene.analysis.Analyzer
 * @see org.apache.lucene.analysis.TokenStream
 * @see org.apache.lucene.analysis.util.CharTokenizer
 * @see LuceneIndexConfig
 */
@Component("AttributeFieldAnalyzer")
public class AttributeFieldAnalyzer extends Analyzer {

  private final LuceneIndexConfig config;

  /**
   * Constructs a new {@code AttributeFieldAnalyzer} instance using the provided
   * {@link LuceneIndexConfig}.
   *
   * @param config the Lucene index configuration providing stopword settings and other parameters
   */
  public AttributeFieldAnalyzer(LuceneIndexConfig config) {
    this.config = config;
  }

  /**
   * Creates the tokenization and filtering pipeline for analyzing field content.
   * The analysis consists of:
   * <ul>
   *   <li>Tokenization using {@link AttributeFieldTokenizer}</li>
   *   <li>Accent folding via {@link ASCIIFoldingFilter}</li>
   *   <li>Stopword removal via {@link StopFilter}</li>
   *   <li>Lowercasing via {@link LowerCaseFilter}</li>
   * </ul>
   *
   * @param fieldName the name of the field being analyzed
   * @return a {@link TokenStreamComponents} with configured tokenizer and filters
   */
  @Override
  protected TokenStreamComponents createComponents(String fieldName) {
    Tokenizer source = new AttributeFieldTokenizer();
    TokenStream filter = new StopFilter(new ASCIIFoldingFilter(source), config.getStopWordsCache());
    filter = new LowerCaseFilter(filter);
    return new TokenStreamComponents(source, filter);
  }

  /**
   * Applies a {@link LowerCaseFilter} normalization step,
   * ensuring case-insensitive token matching during indexing and queries.
   *
   * @param fieldName the field being normalized
   * @param in the input {@link TokenStream}
   * @return a lowercase-normalized {@link TokenStream}
   */
  @Override
  protected TokenStream normalize(String fieldName, TokenStream in) {
    return new LowerCaseFilter(in);
  }

  /**
   * Tokenizer that breaks input text into tokens based on whitespace and ignores
   * specific punctuation symbols such as commas, semicolons, and parentheses.
   */
  private static class AttributeFieldTokenizer extends CharTokenizer {
    /**
     * Defines valid token characters. Non-whitespace characters are considered tokens,
     * except for comma (,), semicolon (;), and parentheses.
     *
     * @param c the character to check
     * @return {@code true} if the character should be part of a token; {@code false} otherwise
     */
    @Override
    protected boolean isTokenChar(int c) {
      return !Character.isWhitespace(c) && !(',' == c || ';' == c || '(' == c || ')' == c);
    }
  }
}
