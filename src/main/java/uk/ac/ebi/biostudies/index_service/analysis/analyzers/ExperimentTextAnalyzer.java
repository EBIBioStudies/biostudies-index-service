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
 * Analyzer implementation dedicated to processing text within experimental data fields.
 *
 * <p>This analyzer standardizes text for indexing by:
 *
 * <ul>
 *   <li>Tokenizing alphanumeric identifiers and compound terms including hyphens (e.g., "COVID-19",
 *       "gene-expression")
 *   <li>Applying ASCII folding to remove accents and normalize diacritics
 *   <li>Removing predefined stop words configured through {@link LuceneIndexConfig}
 *   <li>Lowercasing all tokens for case-insensitive matching
 * </ul>
 *
 * <p>The goal is to improve search matching for experimental terms in scientific metadata.
 */
@Component("ExperimentTextAnalyzer")
public final class ExperimentTextAnalyzer extends Analyzer {

  private final LuceneIndexConfig config;

  /**
   * Creates a new ExperimentTextAnalyzer.
   *
   * @param config Lucene configuration providing access to stop word sets.
   */
  public ExperimentTextAnalyzer(LuceneIndexConfig config) {
    this.config = config;
  }

  /**
   * Defines the analyzer pipeline for tokenizing and filtering the experimental text.
   *
   * <p>Pipeline order:
   *
   * <ol>
   *   <li>{@link ExperimentTextTokenizer}: Generates tokens containing letters, digits, or hyphens.
   *   <li>{@link ASCIIFoldingFilter}: Removes accents (for example, "μ" → "u").
   *   <li>{@link StopFilter}: Removes common irrelevant words (e.g., "the", "of", "and").
   *   <li>{@link LowerCaseFilter}: Normalizes token case for consistent search matching.
   * </ol>
   *
   * @param fieldName the name of the field being analyzed
   * @return a Lucene TokenStreamComponents containing the tokenizer and filters
   */
  @Override
  protected TokenStreamComponents createComponents(String fieldName) {
    Tokenizer source = new ExperimentTextTokenizer();
    TokenStream filter = new ASCIIFoldingFilter(source); // Normalize accents early
    filter = new StopFilter(filter, config.getStopWordsCache()); // Remove stop words
    filter = new LowerCaseFilter(filter); // Apply lowercase normalization
    return new TokenStreamComponents(source, filter);
  }

  /**
   * Normalization step applied during query parsing to ensure text consistency.
   *
   * @param fieldName field name being normalized
   * @param in input token stream
   * @return normalized lowercase token stream
   */
  @Override
  protected TokenStream normalize(String fieldName, TokenStream in) {
    return new LowerCaseFilter(in);
  }

  /**
   * Tokenizer that defines valid token characters for experimental text.
   *
   * <p>This tokenizer treats any sequence of:
   *
   * <ul>
   *   <li>Letters (A–Z, a–z)
   *   <li>Digits (0–9)
   *   <li>Hyphens ('-')
   * </ul>
   *
   * as a single token. Non‑matching characters serve as delimiters.
   */
  private static class ExperimentTextTokenizer extends CharTokenizer {
    @Override
    protected boolean isTokenChar(int c) {
      // Use logical OR (||) to ensure boolean behavior
      return Character.isLetter(c) || Character.isDigit(c) || c == '-';
    }
  }
}
