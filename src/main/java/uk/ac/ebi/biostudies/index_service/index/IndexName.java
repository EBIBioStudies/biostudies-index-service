package uk.ac.ebi.biostudies.index_service.index;

/**
 * Enum representing Lucene indexes names used by the BioStudies indexing service. Each constant
 * corresponds to the name of a specific searchable index in the system.
 */
public enum IndexName {
  /** Main index in the system. It stores data about studies/submissions. */
  SUBMISSION("submission_index"),
  PAGE_TAB("pagetab_index"),
  /** Files metadata */
  FILES("file_index"),
  /** Facets */
  FACET("facet_index"),
  EFO("efo_index"),
  ;

  private final String indexName;

  IndexName(String indexName) {
    this.indexName = indexName;
  }

  public String getIndexName() {
    return indexName;
  }
}
