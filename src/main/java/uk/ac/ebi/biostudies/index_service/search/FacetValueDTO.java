package uk.ac.ebi.biostudies.index_service.search;

import lombok.Data;

/** Facet value with hit count. */
@Data
public class FacetValueDTO {
  private String name; // Display name
  private String value; // Actual value
  private int hits; // Number of matching documents
}
