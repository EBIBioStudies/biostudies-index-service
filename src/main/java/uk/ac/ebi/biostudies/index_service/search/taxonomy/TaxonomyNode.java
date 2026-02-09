package uk.ac.ebi.biostudies.index_service.search.taxonomy;

/**
 * Represents an EFO taxonomy node with term name, submission count, EFO ID, and child indicator.
 *
 * @param term the EFO term label (e.g., "cell type")
 * @param count number of submissions containing this term or its descendants
 * @param efoId the EFO URI (e.g., "http://purl.obolibrary.org/obo/CL_0000000")
 * @param hasChildren whether this term has any children in the EFO ontology
 */
public record TaxonomyNode(String term, int count, String efoId, boolean hasChildren) {

  // Constructor for backward compatibility (defaults hasChildren to true)
  public TaxonomyNode(String term, int count, String efoId) {
    this(term, count, efoId, true);
  }
}
