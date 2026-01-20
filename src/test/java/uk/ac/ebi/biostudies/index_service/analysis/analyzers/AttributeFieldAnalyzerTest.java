package uk.ac.ebi.biostudies.index_service.analysis.analyzers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;

class AttributeFieldAnalyzerTest {

  private AttributeFieldAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    CharArraySet stopWords = new CharArraySet(List.of("and", "the", "of"), true);
    LuceneIndexConfig config = new TestConfig(stopWords);
    analyzer = new AttributeFieldAnalyzer(config);
  }

  @Test
  void testBasicTokenization() throws IOException {
    List<String> tokens = analyze("Hello, world; this (text) works");
    assertEquals(List.of("hello", "world", "this", "text", "works"), tokens);
  }

  @Test
  void testAccentFolding() throws IOException {
    List<String> tokens = analyze("café résumé jalapeño");
    // Should be ASCII folded and lowercased
    assertEquals(List.of("cafe", "resume", "jalapeno"), tokens);
  }

  @Test
  void testStopWordsAreRemoved() throws IOException {
    List<String> tokens = analyze("The power of and light");
    assertEquals(List.of("power", "light"), tokens);
  }

  @Test
  void testLowerCasing() throws IOException {
    List<String> tokens = analyze("MIXED Case TeXT");
    assertEquals(List.of("mixed", "case", "text"), tokens);
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

  // --- Utility method for tokenizing and reading results ---
  private List<String> analyze(String input) throws IOException {
    try (TokenStream ts = analyzer.tokenStream("field", new StringReader(input))) {
      CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
      ts.reset();

      List<String> result = new java.util.ArrayList<>();
      while (ts.incrementToken()) {
        result.add(termAttr.toString());
      }
      ts.end();
      return result;
    }
  }

}
