package uk.ac.ebi.biostudies.index_service.metadata;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IndexMetadataDto {
  private String name;
  private String location;
  private LocalDateTime updateTime;
  private double size;
  private long numberOfDocuments;
}
