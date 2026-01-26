package uk.ac.ebi.biostudies.index_service.index.efo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Lucene field names for EFO ontology index.
 * Used during indexing and searching of EFO terms, hierarchies, and synonyms.
 */
@Getter
@RequiredArgsConstructor
public enum EFOField {
  /** Unique identifier (IRI) */
  ID("id"),

  /** EFO accession (e.g., EFO_0000001) */
  EFO_ID("efoid"),

  /** Primary term label */
  TERM("term"),

  /** Alternative term synonyms (hasExactSynonym) */
  ALTERNATIVE_TERMS("altterm"),

  /** Full-text searchable content (all terms combined) */
  CONTENT("content"),

  /** Child node IDs (subClassOf hierarchy) */
  CHILDREN("child"),  // ← Fixed typo: CHILDRERN

  /** Parent node IDs (superClassOf) */
  PARENT("father"),  // Consider renaming value to "parent"

  /** Aggregated field (all terms + alts) */
  ALL("all");

  private final String fieldName;

  @Override
  public String toString() {
    return fieldName;
  }
}