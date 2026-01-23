package uk.ac.ebi.biostudies.index_service.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SearchService {
  public String search(SearchRequest request) {
    log.info("Searching for {}", request);
    return null;
  }
}
