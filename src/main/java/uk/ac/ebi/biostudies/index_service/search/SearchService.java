package uk.ac.ebi.biostudies.index_service.search;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SearchService {
  public SearchResponseDTO search(SearchRequest request) {
    log.info("Searching for {}", request);
    SearchResponseDTO response =
        new SearchResponseDTO(
            1, // page
            20, // pageSize
            0L, // totalHits
            true, // isTotalHitsExact
            "links", // sortBy
            "descending", // sortOrder
            List.of(), // suggestion
            List.of(), // expandedEfoTerms
            List.of(), // expandedSynonyms
            "author:smith AND title:bacteria", // query
            Map.of("facet.collection", List.of("europepmc")), // facets
            List.of() // hits
            );
    return response;
  }
}
