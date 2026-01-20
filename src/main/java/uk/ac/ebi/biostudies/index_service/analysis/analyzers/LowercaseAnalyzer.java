package uk.ac.ebi.biostudies.index_service.analysis.analyzers;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.springframework.stereotype.Component;

/**
 * Analyzer that tokenizes text into sequences of letters and normalizes all tokens to lowercase.
 *
 * <p>This analyzer splits input text into tokens containing only letters (A-Z and a-z) via {@link
 * LetterTokenizer} and applies lowercase normalization during query parsing and indexing via {@link
 * LowerCaseFilter}.
 *
 * <p>It is useful for indexing fields where only alphabetic tokens matter and case-insensitive
 * matching is desired.
 */
@Component("LowercaseAnalyzer")
public final class LowercaseAnalyzer extends Analyzer {

  /**
   * Creates the analyzer tokenization pipeline.
   *
   * @param fieldName the field name (not used in this analyzer)
   * @return {@link TokenStreamComponents} with a letter tokenizer as the token source
   */
  @Override
  protected TokenStreamComponents createComponents(String fieldName) {
    Tokenizer source = new LetterTokenizer();
    TokenStream filter = new LowerCaseFilter(source);
    return new TokenStreamComponents(source, filter);
  }

  /**
   * Normalizes the token stream by applying lowercase filter.
   *
   * @param fieldName the field name being normalized
   * @param in the input token stream
   * @return token stream with lowercase normalization
   */
  @Override
  protected TokenStream normalize(String fieldName, TokenStream in) {
    return new LowerCaseFilter(in);
  }
}
