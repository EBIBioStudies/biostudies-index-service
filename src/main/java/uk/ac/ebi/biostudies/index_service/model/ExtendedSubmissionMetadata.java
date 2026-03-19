package uk.ac.ebi.biostudies.index_service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import lombok.Data;
import uk.ac.ebi.biostudies.index_service.client.ExtSubmissionHttpClient.SubmissionFetchException;

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

  public static ExtendedSubmissionMetadata fromJsonNode(JsonNode rawJson, ObjectMapper objectMapper) {
    try {
      ExtendedSubmissionMetadata meta = objectMapper.treeToValue(rawJson, ExtendedSubmissionMetadata.class);
      meta.setRawSubmissionJson(rawJson);
      return meta;
    } catch (JsonProcessingException e) {
      String accNo = rawJson.path("accNo").asText("unknown");
      throw new IllegalStateException(
          String.format("Failed to parse submission %s: %s", accNo, e.getMessage()), e);
    }
  }
}
