package uk.ac.ebi.biostudies.index_service.index.efo;

/**
 * Field name constants for the EFO expansion index.
 *
 * <p>These constants are shared between the EFO indexer and the query expander to ensure
 * consistency.
 */
public final class EFOIndexFields {

  /** Field name for searchable expansion terms (keys) */
  public static final String TERM = "qe.term";

  /** Field name for EFO ontology identifiers */
  public static final String EFO = "qe.efo";

  private EFOIndexFields() {
    // Utility class, prevent instantiation
  }
}
