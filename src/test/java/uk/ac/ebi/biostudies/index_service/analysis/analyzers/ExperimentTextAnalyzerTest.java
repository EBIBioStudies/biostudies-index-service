package uk.ac.ebi.biostudies.index_service.analysis.analyzers;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;

class ExperimentTextAnalyzerTest {

  private ExperimentTextAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    CharArraySet stopWords = new CharArraySet(List.of("and", "the", "of"), true);
    LuceneIndexConfig config = new TestConfig(stopWords);
    analyzer = new ExperimentTextAnalyzer(config);
  }

  @Test
  void shouldTokenizeAlphanumericAndHyphens() throws IOException {
    String text = "gene-expression-1234";
    List<String> expectedTokens = List.of("gene-expression-1234");
    assertAnalyzesTo(analyzer, text, expectedTokens);
  }

  @Test
  void shouldRemoveStopWordsAndLowercase() throws IOException {
    String text = "The analysis of gene expression";
    List<String> expectedTokens = List.of("analysis", "gene", "expression");
    assertAnalyzesTo(analyzer, text, expectedTokens);
  }

  @Test
  void shouldFoldAccentedCharacters() throws IOException {
    String text = "Café München-12";
    List<String> expectedTokens = List.of("cafe", "munchen-12");
    assertAnalyzesTo(analyzer, text, expectedTokens);
  }

  @Test
  void testNormalizationIsLowerCase() throws IOException {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader("MiXeD"));

    TokenStream normalized = analyzer.normalize("field", tokenizer);
    CharTermAttribute termAttr = normalized.addAttribute(CharTermAttribute.class);
    normalized.reset();

    assertTrue(normalized.incrementToken());
    assertEquals("mixed", termAttr.toString());

    normalized.end();
    normalized.close();
  }

  private void assertAnalyzesTo(Analyzer analyzer, String text, List<String> expectedTokens)
      throws IOException {
    try (TokenStream stream = analyzer.tokenStream("field", text)) {
      CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
      stream.reset();
      List<String> actualTokens = new java.util.ArrayList<>();
      while (stream.incrementToken()) {
        actualTokens.add(attr.toString());
      }
      stream.end();
      assertEquals(expectedTokens, actualTokens);
    }
  }
}
