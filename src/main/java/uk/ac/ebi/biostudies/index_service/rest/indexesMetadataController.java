package uk.ac.ebi.biostudies.index_service.rest;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.biostudies.index_service.metadata.IndexMetadataDto;
import uk.ac.ebi.biostudies.index_service.metadata.IndexesMetadataService;

@RestController
@RequestMapping("/internal/api/indexes")

public class indexesMetadataController {

  private final IndexesMetadataService indexesMetadataService;

  public indexesMetadataController(IndexesMetadataService indexesMetadataService) {
    this.indexesMetadataService = indexesMetadataService;
  }

  @GetMapping( value = "/metadata", produces = "application/json")
  public List<IndexMetadataDto> getIndexes() {
    return indexesMetadataService.getAllIndexesMetadata();
  }

}
