package uk.ac.ebi.biostudies.index_service.index.efo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EFOManager {
  private final EFOLoader loader;

  public EFOManager(EFOLoader loader) {
    this.loader = loader;
  }

  public void loadEFO() {
    log.info("Loading EFO model");
    EFOTermResolver r = loader.getResolver();
    EFOModel model = r.getModel();
    System.out.println(model.getNodeCount());
  }

}
