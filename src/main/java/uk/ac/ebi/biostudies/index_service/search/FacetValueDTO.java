package uk.ac.ebi.biostudies.index_service.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Represents a single facet value with its display name, filter value, and document count.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FacetValueDTO {
  /** Display name shown in the UI (e.g., "ArrayExpress"). */
  private String name;

  /** Actual filter value used in queries (e.g., "arrayexpress"). */
  private String value;

  /** Number of documents matching this facet value. */
  private int hits;
}