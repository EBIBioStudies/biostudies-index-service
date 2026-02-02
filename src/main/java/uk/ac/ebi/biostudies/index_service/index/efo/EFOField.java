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
  EFO_ID("efo_id"),

  /** Primary term label */
  TERM("term"),

  /** Alternative term synonyms (hasExactSynonym) */
  ALTERNATIVE_TERMS("alt_term"),

  /** Full-text searchable content (all terms combined) */
  CONTENT("content"),

  /** Child node IDs (subClassOf hierarchy) */
  CHILDREN("child"),

  /** Parent node IDs (superClassOf) */
  PARENT("parent"),

  /** Query extension term */
  QE_TERM("qe.term"),

  /** Query extension EFO */
  QE_EFO("qe.efo"),

  /** Aggregated field (all terms + alts) */
  ALL("all");

  private final String fieldName;

  @Override
  public String toString() {
    return fieldName;
  }
}