package uk.ac.ebi.biostudies.index_service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtendedSubmissionMetadata {
  private String accNo;
  private int version;
  private String schemaVersion;
  private String owner;
  private String submitter;
  private String title;
  private String doi;
  private String method;
  private String relPath;
  private String rootPath;
  private boolean released;
  private String secretKey;
  private OffsetDateTime releaseTime;
  private OffsetDateTime submissionTime;
  private OffsetDateTime modificationTime;
  private OffsetDateTime creationTime;
  private String storageMode;
  private JsonNode rawSubmissionJson;
}
