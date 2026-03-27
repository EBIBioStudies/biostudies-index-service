package uk.ac.ebi.biostudies.index_service.rest;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import uk.ac.ebi.biostudies.index_service.metadata.IndexMetadataDto;

@Data
public class IndexesMetadataResponse {
  private LocalDateTime timestamp;
  private List<IndexMetadataDto> indexes;
}
