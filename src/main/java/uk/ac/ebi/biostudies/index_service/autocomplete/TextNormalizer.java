package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes text for EFO term matching.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Lowercase everything
 *   <li>Unicode-normalize and remove diacritics
 *   <li>Replace punctuation and symbols with spaces
 *   <li>Keep letters, digits, and token order
 *   <li>Collapse repeated whitespace
 * </ul>
 */
public final class TextNormalizer {

  private TextNormalizer() {
    // Utility class
  }

  /**
   * Normalizes raw text into a match-friendly form.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code "alpha-amino-N-butyric acid measurement"} → {@code "alpha amino n butyric acid
   *       measurement"}
   *   <li>{@code "1,5 anhydroglucitol measurement"} → {@code "1 5 anhydroglucitol measurement"}
   *   <li>{@code "alpha thalassemia-intellectual disability syndrome type 1"} → {@code "alpha
   *       thalassemia intellectual disability syndrome type 1"}
   *   <li>{@code "GATA1-Related X-Linked Cytopenia"} → {@code "gata1 related x linked cytopenia"}
   * </ul>
   *
   * @param input raw text, may be null
   * @return normalized text, never null
   */
  public static String normalize(String input) {
    if (input == null || input.isBlank()) {
      return "";
    }

    String text = Normalizer.normalize(input, Normalizer.Form.NFKD);
    text = text.replaceAll("\\p{M}+", ""); // remove combining marks
    text = text.toLowerCase();

    StringBuilder out = new StringBuilder(text.length());
    boolean lastWasSpace = true;

    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);

      if (Character.isLetterOrDigit(ch)) {
        out.append(ch);
        lastWasSpace = false;
      } else {
        // Treat anything else as a separator
        if (!lastWasSpace) {
          out.append(' ');
          lastWasSpace = true;
        }
      }
    }

    return out.toString().trim().replaceAll("\\s+", " ");
  }

  /**
   * Tokenizes input after normalization.
   *
   * @param input raw text, may be null
   * @return tokens, never null
   */
  public static String[] tokenize(String input) {
    String norm = normalize(input);
    if (norm.isEmpty()) {
      return new String[0];
    }
    return norm.split(" ");
  }

  /**
   * Tokenizes input into a mutable list.
   *
   * @param input raw text, may be null
   * @return token list, never null
   */
  public static List<String> tokenizeAsList(String input) {
    String[] tokens = tokenize(input);
    List<String> result = new ArrayList<>(tokens.length);
    for (String token : tokens) {
      if (!token.isBlank()) {
        result.add(token);
      }
    }
    return result;
  }
}
