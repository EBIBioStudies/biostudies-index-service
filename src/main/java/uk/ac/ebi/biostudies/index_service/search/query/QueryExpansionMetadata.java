package uk.ac.ebi.biostudies.index_service.search.query;

import java.util.Collections;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class QueryExpansionMetadata {

  /** List of EFO ontology terms added */
  private final List<String> efoTerms;

  /** List of synonym terms added */
  private final List<String> synonyms;

  /** Original term that was expanded */
  private final String originalTerm;

  /** Whether expansion was limited due to too many terms */
  private final boolean limitedByMaxTerms;

  /** Maximum number of expansion terms allowed */
  private final int maxExpansionTerms;

  /** Total number of terms added (EFO + synonyms) */
  public int getTotalExpansionCount() {
    return efoTerms.size() + synonyms.size();
  }

  /** Whether any expansion occurred */
  public boolean isExpanded() {
    return !efoTerms.isEmpty() || !synonyms.isEmpty();
  }

  /**
   * Creates an empty expansion metadata (no expansion occurred).
   *
   * @return empty metadata instance
   */
  public static QueryExpansionMetadata empty() {
    return new QueryExpansionMetadata(
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        false,
        0
    );
  }
}
