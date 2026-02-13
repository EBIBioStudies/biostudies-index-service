package uk.ac.ebi.biostudies.index_service.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

/**
 * Represents a facet dimension (group) for UI display.
 * Contains metadata about the facet and its possible values.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // Omit null fields from JSON
public class FacetDimensionDTO {
  /** Programmatic name of the facet (e.g., "facet.collection"). */
  private String name;

  /** Human-readable title for display (e.g., "Collection"). */
  private String title;

  /** Optional facet type indicator (e.g., "boolean"). */
  private String type;

  /** List of possible values for this facet with their hit counts. */
  private List<FacetValueDTO> children;
}
