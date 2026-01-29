package uk.ac.ebi.biostudies.index_service.search;

import lombok.Data;

/** Search hit (document result). */
@Data
public class HitDTO {
  private String accession;
  private String title;
  private String description;
}
