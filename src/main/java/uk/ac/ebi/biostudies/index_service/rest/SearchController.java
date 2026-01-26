package uk.ac.ebi.biostudies.index_service.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;
import uk.ac.ebi.biostudies.index_service.search.SearchRequest;
import uk.ac.ebi.biostudies.index_service.search.SearchResponseDTO;
import uk.ac.ebi.biostudies.index_service.search.SearchService;

/**
 * REST controller for BioStudies search API v1 endpoint. Processes search queries with pagination,
 * sorting, facets, and field filtering.
 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {

  /** Query parameters ignored during facet/field processing (case-insensitive). */
  private static final Set<String> IGNORED_PARAMS =
      Set.of("pagesize", "page", "sortby", "sortorder", "query", "limit");

  private final CollectionRegistryService collectionRegistryService;

  /** Thread-safe lazy cache for registry field names. Initialized on first access. */
  private volatile Set<String> fieldNames; // volatile for DCL pattern

  private final SearchService searchService;

  public SearchController(CollectionRegistryService collectionRegistryService,
      SearchService searchService) {
    this.collectionRegistryService = collectionRegistryService;
    this.searchService = searchService;
  }

  /**
   * Returns cached field names from collection registry. Lazy-initialized with double-checked
   * locking for thread safety.
   *
   * @throws IllegalStateException if registry not initialized
   */
  private Set<String> getFieldNames() {
    if (fieldNames == null) {
      synchronized (this) {
        if (fieldNames == null) {
          CollectionRegistry registry = collectionRegistryService.getCurrentRegistry();
          if (registry == null) {
            throw new IllegalStateException(
                "Collection registry not initialized. "
                    + "Ensure ApplicationReadyEvent initialization completes first.");
          }
          fieldNames = Set.copyOf(registry.getGlobalPropertyRegistry().keySet());
        }
      }
    }
    return fieldNames;
  }

  /**
   * Search endpoint supporting free-text queries, pagination, sorting, facets (?facet.organ=lung),
   * and field filters (?title=foo). Returns paginated JSON results.
   *
   * @param query free-text query (prioritizes ?query= param if present)
   * @param page 1-based page number (default 1)
   * @param pageSize results per page (default 20)
   * @param sortBy sort field (default relevance/empty)
   * @param sortOrder ascending/descending (default descending)
   * @param params remaining params processed as facets or fields
   */
  @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SearchResponseDTO> search(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize,
      @RequestParam(defaultValue = "") String sortBy,
      @RequestParam(defaultValue = "descending") String sortOrder,
      @RequestParam MultiValueMap<String, String> params) {

    // Prioritize query from params over explicit param (matches old behavior)
    String queryString = params.getFirst("query") != null ? params.getFirst("query") : query;

    SearchRequest searchRequest = new SearchRequest();
    searchRequest.setQuery(queryString);
    searchRequest.setPage(page);
    searchRequest.setPageSize(pageSize);
    searchRequest.setSortBy(sortBy);
    searchRequest.setSortOrder(sortOrder);

    populateFacetFields(params, searchRequest);

    // Call search service and get response
    SearchResponseDTO response = searchService.search(searchRequest);

    searchService.search(searchRequest);

    // TODO: Inject SearchService and call searchService.search(searchRequest);
    return ResponseEntity.ok(response);
  }

  /**
   * Populates {@link SearchRequest#facets} and {@link SearchRequest#fields} from query params.
   * Validates fields against collection registry. Facets prefixed with "facet.".
   */
  private void populateFacetFields(MultiValueMap<String, String> params, SearchRequest request) {
    if (params == null || params.isEmpty()) return;

    params.forEach(
        (key, values) -> {
          if (IGNORED_PARAMS.contains(key.toLowerCase())) return;

          if (isFacetParam(key)) {
            String facetName = extractFacetName(key);
            request.getFacets().put(facetName, new ArrayList<>(values));
          } else if (getFieldNames().contains(key.toLowerCase())) {
            request.getFields().put(key, List.of(values.get(0))); // Single-value fields
          }
        });
  }

  /**
   * Extracts facet name by removing trailing "[]" (multi-value array notation).
   *
   * @param key raw parameter key (e.g. "facet.organ[]" → "facet.organ")
   */
  private String extractFacetName(String key) {
    return key.endsWith("[]") ? key.substring(0, key.length() - 2) : key;
  }

  /** Tests if parameter represents a facet filter (contains "facet."). */
  private boolean isFacetParam(String key) {
    return key.toLowerCase().contains("facet.");
  }
}
