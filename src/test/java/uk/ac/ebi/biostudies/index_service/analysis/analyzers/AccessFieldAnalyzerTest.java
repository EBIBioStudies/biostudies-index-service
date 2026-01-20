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

class AccessFieldAnalyzerTest {

  private AccessFieldAnalyzer analyzer;
  private LuceneIndexConfig config;

  @BeforeEach
  void setUp() {
    CharArraySet stopWords = new CharArraySet(List.of("and", "the", "of"), true);
    LuceneIndexConfig config = new TestConfig(stopWords);

    analyzer = new AccessFieldAnalyzer(config);
  }

  @Test
  void shouldTokenizeAlphanumericAndSpecialChars() throws IOException {
    String text = "user.name-email_123@example.com";
    List<String> expected = List.of(
        "user.name-email_123@example.com"
    );
    assertAnalyzesTo(analyzer, text, expected);
  }

  @Test
  void shouldRemoveStopWordsAndLowercase() throws IOException {
    String text = "The User And The Admin";
    List<String> expected = List.of("user", "admin");
    assertAnalyzesTo(analyzer, text, expected);
  }

  @Test
  void shouldFoldAccentedCharacters() throws IOException {
    String text = "Café Münster";
    List<String> expected = List.of("cafe", "munster");
    assertAnalyzesTo(analyzer, text, expected);
  }

  @Test
  void shouldNormalizeToLowercase() throws IOException {
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
    TokenStream stream = analyzer.tokenStream("access", input);
    CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
    stream.reset();

    List<String> actualTokens = new java.util.ArrayList<>();
    while (stream.incrementToken()) {
      actualTokens.add(termAttr.toString());
    }
    stream.end();
    stream.close();

    assertEquals(expectedTokens, actualTokens);
  }
}
