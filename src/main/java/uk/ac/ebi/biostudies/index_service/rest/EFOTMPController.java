package uk.ac.ebi.biostudies.index_service.rest;

import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOManager;

// To test loading efo data. DELETE later
@Slf4j
@RestController
@RequestMapping("/internal/api/efo")
public class EFOTMPController {
  private final EFOManager efoManager;

  public EFOTMPController(EFOManager efoManager) {
    this.efoManager = efoManager;
  }

  @PostMapping("/load") //
  public ResponseEntity<RestResponse<String>> testLoad() {
    efoManager.loadEFO();
    return ResponseEntity.accepted().body(new RestResponse<>(true, "ok", null, List.of()));
  }

  @PostMapping("/index") //
  public ResponseEntity<RestResponse<String>> testIndex() throws IOException {
    efoManager.indexEFO();
    return ResponseEntity.accepted().body(new RestResponse<>(true, "ok", null, List.of()));
  }
}
