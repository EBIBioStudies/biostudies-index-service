package uk.ac.ebi.biostudies.index_service.index.management.init;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.search.suggestion.SpellCheckSuggestionService;

/**
 * Initializer for the spell checker, which provides query spelling suggestions.
 *
 * <p>Uses Lucene's {@link DirectSpellChecker} which works directly on indexes without requiring a
 * separate spell checking index. This approach is more efficient and always reflects the current
 * state of the index.
 *
 * <p><strong>Dependencies:</strong> Requires indexes to be open for suggestions to work.
 */
@Slf4j
@Component
public class SpellCheckerInitializer {

  private final SpellCheckSuggestionService suggestionService;

  public SpellCheckerInitializer(SpellCheckSuggestionService suggestionService) {
    this.suggestionService = suggestionService;
  }

  /**
   * Initializes the direct spell checker with optimized settings for BioStudies content.
   *
   * <p>Configuration details:
   *
   * <ul>
   *   <li>Minimum prefix: 2 characters must match exactly
   *   <li>Max edit distance: 2 (Levenshtein distance)
   *   <li>Min query length: 3 (shorter queries don't get suggestions)
   *   <li>Max query frequency: 1% (ignores very common terms)
   *   <li>Threshold frequency: 0 (includes rare terms)
   *   <li>Accuracy: 0.5 (minimum similarity score 0-1)
   * </ul>
   */
  public void initialize() {
    log.info("Initializing DirectSpellChecker");

    DirectSpellChecker spellChecker = new DirectSpellChecker();

    // Configure spell checker parameters
    spellChecker.setMinPrefix(2); // Minimum prefix length that must match
    spellChecker.setMaxEdits(2); // Maximum edit distance (Levenshtein distance)
    spellChecker.setMinQueryLength(3); // Don't suggest for very short queries
    spellChecker.setMaxQueryFrequency(0.01f); // Ignore very common terms (top 1%)
    spellChecker.setThresholdFrequency(0.0f); // Minimum term frequency in index
    spellChecker.setAccuracy(0.5f); // Minimum similarity score (0-1)

    suggestionService.setSpellChecker(spellChecker);
    log.info("DirectSpellChecker initialized successfully");
  }

  /** No cleanup needed for DirectSpellChecker as it doesn't hold resources. */
  public void close() {
    log.debug("DirectSpellChecker requires no cleanup");
  }
}
