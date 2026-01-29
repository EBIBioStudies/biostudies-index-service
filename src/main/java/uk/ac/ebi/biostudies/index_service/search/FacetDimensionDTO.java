package uk.ac.ebi.biostudies.index_service.search;

import java.util.List;
import lombok.Data;

/** Facet dimension for UI display. */
@Data
public class FacetDimensionDTO {
  private String name;
  private String title;
  private String type; // e.g., "boolean"
  private List<FacetValueDTO> children;
}
