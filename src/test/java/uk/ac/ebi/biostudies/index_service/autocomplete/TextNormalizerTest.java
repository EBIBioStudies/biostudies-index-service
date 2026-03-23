package uk.ac.ebi.biostudies.index_service.autocomplete;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TextNormalizerTest {

  static Stream<Arguments> normalizationCases() {
    return Stream.of(
        // Keep letters/digits/spaces
        Arguments.of("hello world 123", "hello world 123"),

        // Convert punctuation/symbols to spaces
        Arguments.of("hello,world!", "hello world"),
        Arguments.of("TNF-alpha", "tnf alpha"),
        Arguments.of("gene_A/gene-B", "gene a gene b"),
        Arguments.of("TNF-α (Tumor necrosis factor)", "tnf α tumor necrosis factor"),

        // Collapse repeated whitespace
        Arguments.of("hello   world\t\n  foo", "hello world foo"),

        // Trim ends
        Arguments.of("  trimmed  ", "trimmed"),

        // Lowercase
        Arguments.of("HELLO World", "hello world"),

        // Unicode normalization (accents -> base letters)
        Arguments.of("café naïve résumé", "cafe naive resume"),

        // Combining diacritics
        Arguments.of("na\u0308i\u0308ve\u0308", "naive"),

        // Mixed symbols/tags/brackets/quotes
        Arguments.of(
            "test<xml>tag</xml>[brackets]'quotes'\"double\"",
            "test xml tag xml brackets quotes double"),
        // Dots, ampersands, dashes, underscores, slashes
        Arguments.of("A.B & C-D/E_F", "a b c d e f"),

        // Numbers with symbols
        Arguments.of("IL2Rα 123.45% 67/89", "il2rα 123 45 67 89"),

        // All whitespace types
        Arguments.of("a\tb\nc\rr  d\u00A0e", "a b c r d e"));
  }

  private static Stream<Arguments> tokenizationCases() {
    return Stream.of(
        Arguments.of(
            "alpha-amino-N-butyric acid measurement",
            new String[] {"alpha", "amino", "n", "butyric", "acid", "measurement"}),
        Arguments.of(
            "1,5 anhydroglucitol measurement",
            new String[] {"1", "5", "anhydroglucitol", "measurement"}),
        Arguments.of(
            "alpha thalassemia-intellectual disability syndrome type 1",
            new String[] {
              "alpha", "thalassemia", "intellectual", "disability", "syndrome", "type", "1"
            }),
        Arguments.of(
            "GATA1-Related X-Linked Cytopenia",
            new String[] {"gata1", "related", "x", "linked", "cytopenia"}),
        Arguments.of(null, new String[0]),
        Arguments.of("", new String[0]),
        Arguments.of("   ", new String[0]));
  }

  @ParameterizedTest
  @MethodSource("normalizationCases")
  void normalize_handlesAllRules(String input, String expected) {
    String actual = TextNormalizer.normalize(input);
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void normalize_returnsEmptyForNullOrEmpty(String input) {
    assertEquals("", TextNormalizer.normalize(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "\t", "\n", "\r", "\u00A0"})
  void normalize_collapsesAllWhitespaceTypes(String input) {
    assertEquals("", TextNormalizer.normalize(input));
  }

  @Test
  void normalize_preservesNonLatinLetters() {
    // Greek letters should be preserved (they're \p{L})
    assertEquals("α β γ", TextNormalizer.normalize("Α Β Γ"));

    // Cyrillic
    assertEquals("привет мир", TextNormalizer.normalize("Привет Мир"));
  }

  @ParameterizedTest
  @MethodSource("tokenizationCases")
  void tokenize_producesCorrectTokens(String input, String[] expected) {
    String[] actual = TextNormalizer.tokenize(input);
    assertArrayEquals(expected, actual);
  }

  @ParameterizedTest
  @MethodSource("normalizationCases")
  void normalize_producesExpectedText(String input, String expected) {
    assertEquals(expected, TextNormalizer.normalize(input));
  }

  @Test
  void tokenize_emptyNormalizedText_returnsEmptyArray() {
    assertEquals(0, TextNormalizer.tokenize("!@#$").length);
  }
}
