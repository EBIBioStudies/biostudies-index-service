package uk.ac.ebi.biostudies.index_service.rest;

import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.biostudies.index_service.metadata.IndexesMetadataService;

@RestController
@RequestMapping("/internal/api/indexes")
public class indexesMetadataController {

  private final IndexesMetadataService indexesMetadataService;

  public indexesMetadataController(IndexesMetadataService indexesMetadataService) {
    this.indexesMetadataService = indexesMetadataService;
  }

  @GetMapping(value = "/metadata", produces = "application/json")
  public ResponseEntity<RestResponse<IndexesMetadataResponse>> getIndexes() {
    IndexesMetadataResponse response = new IndexesMetadataResponse();
    response.setTimestamp(LocalDateTime.now());
    response.setIndexes(indexesMetadataService.getAllIndexesMetadata());

    return ResponseEntity.ok(RestResponse.success("Indexes metadata retrieved", response));
  }
}
