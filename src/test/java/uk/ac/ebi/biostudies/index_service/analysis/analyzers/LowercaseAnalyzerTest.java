package uk.ac.ebi.biostudies.index_service.analysis.analyzers;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

class LowercaseAnalyzerTest {

  private final LowercaseAnalyzer analyzer = new LowercaseAnalyzer();

  @Test
  void shouldTokenizeOnlyLetters() throws IOException {
    String text = "Hello123 World! 456";
    List<String> expectedTokens = List.of("hello", "world");
    assertAnalyzesTo(analyzer, text, expectedTokens);
  }

  @Test
  void shouldNormalizeTokensToLowercase() throws IOException {
    String text = "Mixed CASE Input";
    List<String> expectedTokens = List.of("mixed", "case", "input");
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

  private void assertAnalyzesTo(Analyzer analyzer, String input, List<String> expectedTokens)
      throws IOException {
    try (TokenStream stream = analyzer.tokenStream("field", input)) {
      CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
      stream.reset();

      List<String> actualTokens = new java.util.ArrayList<>();
      while (stream.incrementToken()) {
        actualTokens.add(termAttr.toString());
      }
      stream.end();
      assertEquals(expectedTokens, actualTokens);
    }
  }
}
