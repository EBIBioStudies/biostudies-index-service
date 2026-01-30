package uk.ac.ebi.biostudies.index_service.index.management.init;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Initializer for the spell checker, which provides query spelling suggestions.
 *
 * <p>Uses Lucene's {@link DirectSpellChecker} which works directly on the submission index without
 * requiring a separate spell checking index. This approach is more efficient and always reflects
 * the current state of the index.
 *
 * <p><strong>Dependencies:</strong> Requires submission index to be open for suggestions to work.
 */
@Slf4j
@Component
public class SpellCheckerInitializer {

  private final IndexManager indexManager;

  public SpellCheckerInitializer(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  /**
   * Initializes the direct spell checker.
   *
   * <p>Creates a {@link DirectSpellChecker} instance with optimized settings for BioStudies content
   * and stores it in the index manager.
   *
   * <p>The spell checker works directly on the submission index, so no separate dictionary building
   * is required.
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

    indexManager.setSpellChecker(spellChecker);
    log.info("DirectSpellChecker initialized successfully");
  }

  /**
   * No cleanup needed for DirectSpellChecker as it doesn't hold resources.
   */
  public void close() {
    log.debug("DirectSpellChecker requires no cleanup");
  }
}
