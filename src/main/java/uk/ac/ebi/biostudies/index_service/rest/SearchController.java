package uk.ac.ebi.biostudies.index_service.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@Tag(
    name = "Search",
    description = "BioStudies search operations with faceted filtering and pagination")
public class SearchController {

  /** Query parameters ignored during facet/field processing (case-insensitive). */
  private static final Set<String> IGNORED_PARAMS =
      Set.of("pagesize", "page", "sortby", "sortorder", "query", "limit");

  private final CollectionRegistryService collectionRegistryService;
  private final SearchService searchService;

  /** Thread-safe lazy cache for registry field names. Initialized on first access. */
  private volatile Set<String> fieldNames; // volatile for DCL pattern

  public SearchController(
      CollectionRegistryService collectionRegistryService, SearchService searchService) {
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
  @Operation(
      summary = "Search BioStudies submissions",
      description =
          "Performs a comprehensive search across BioStudies submissions with support for:\n"
              + "- **Free-text queries**: Full-text search across indexed fields\n"
              + "- **Faceted filtering**: Apply filters using `facet.<fieldname>=value` (e.g., `facet.organ=lung`)\n"
              + "- **Field filtering**: Filter by specific fields registered in the collection registry (e.g., `title=cancer`)\n"
              + "- **Pagination**: Control result pages with `page` and `pageSize`\n"
              + "- **Sorting**: Order results by specific fields with `sortBy` and `sortOrder`\n\n"
              + "**Dynamic Parameters**: Any parameter matching a registered field name in the collection registry "
              + "will be treated as a field filter. Parameters prefixed with `facet.` are treated as faceted filters.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Search completed successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SearchResponseDTO.class),
                    examples =
                        @ExampleObject(
                            name = "Sample search response",
                            value =
                                "{\n"
                                    + "  \"hits\": [\n"
                                    + "    {\n"
                                    + "      \"accession\": \"S-BSST1432\",\n"
                                    + "      \"title\": \"Pharyngeal neuronal mechanisms...\",\n"
                                    + "      \"releaseDate\": \"2124-05-21\"\n"
                                    + "    }\n"
                                    + "  ],\n"
                                    + "  \"totalHits\": 42,\n"
                                    + "  \"page\": 1,\n"
                                    + "  \"pageSize\": 20,\n"
                                    + "  \"sortBy\": \"releaseDate\",\n"
                                    + "  \"sortOrder\": \"descending\"\n"
                                    + "}"))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error - collection registry not initialized",
            content = @Content)
      })
  @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SearchResponseDTO> search(
      @Parameter(
              description = "Free-text search query. Searches across all indexed text fields.",
              example = "Drosophila melanogaster")
          @RequestParam(defaultValue = "")
          String query,
      @Parameter(
              description = "1-based page number for pagination. Must be >= 1.",
              example = "1",
              schema = @Schema(minimum = "1", defaultValue = "1"))
          @RequestParam(defaultValue = "1")
          Integer page,
      @Parameter(
              description = "Number of results per page. Controls pagination size.",
              example = "20",
              schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
          @RequestParam(defaultValue = "20")
          Integer pageSize,
      @Parameter(
              description =
                  "Field name to sort results by. Leave empty for relevance-based sorting. "
                      + "Common values: 'releaseDate', 'accession', 'title'.",
              example = "releaseDate")
          @RequestParam(defaultValue = "")
          String sortBy,
      @Parameter(
              description = "Sort direction. Either 'ascending' or 'descending'.",
              example = "descending",
              schema = @Schema(allowableValues = {"ascending", "descending"}))
          @RequestParam(defaultValue = "descending")
          String sortOrder,
      @Parameter(
              description =
                  "Additional dynamic parameters for faceted filtering and field filtering:\n"
                      + "- **Facet filters**: Use `facet.<fieldname>=value` (e.g., `facet.organ=lung`)\n"
                      + "- **Field filters**: Use any registered field name (e.g., `title=cancer`)\n"
                      + "- Multi-value facets can use array notation: `facet.organism[]=human&facet.organism[]=mouse`",
              example = "facet.organ=lung&title=cancer")
          @RequestParam
          MultiValueMap<String, String> params) {

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

    return ResponseEntity.ok(response);
  }

  /**
   * Collection-scoped search endpoint supporting free-text queries, pagination, sorting, facets
   * (?facet.organ=lung), and field filters (?title=foo). Returns paginated JSON results filtered by
   * collection.
   *
   * @param collection collection identifier to scope the search (e.g., "bioimages", "arrayexpress")
   * @param query free-text query (prioritizes ?query= param if present)
   * @param page 1-based page number (default 1)
   * @param pageSize results per page (default 20)
   * @param sortBy sort field (default relevance/empty)
   * @param sortOrder ascending/descending (default descending)
   * @param params remaining params processed as facets or fields
   */
  @Operation(
      summary = "Search BioStudies submissions within a specific collection",
      description =
          "Performs a comprehensive search across BioStudies submissions scoped to a specific collection with support for:\n"
              + "- **Collection filtering**: Results are automatically filtered to the specified collection\n"
              + "- **Free-text queries**: Full-text search across indexed fields\n"
              + "- **Faceted filtering**: Apply filters using `facet.<fieldname>=value` (e.g., `facet.organ=lung`)\n"
              + "- **Field filtering**: Filter by specific fields registered in the collection registry (e.g., `title=cancer`)\n"
              + "- **Pagination**: Control result pages with `page` and `pageSize`\n"
              + "- **Sorting**: Order results by specific fields with `sortBy` and `sortOrder`\n\n"
              + "**Dynamic Parameters**: Any parameter matching a registered field name in the collection registry "
              + "will be treated as a field filter. Parameters prefixed with `facet.` are treated as faceted filters.")
  @ApiResponses(
      value = {
          @ApiResponse(
              responseCode = "200",
              description = "Search completed successfully",
              content =
              @Content(
                  mediaType = MediaType.APPLICATION_JSON_VALUE,
                  schema = @Schema(implementation = SearchResponseDTO.class),
                  examples =
                  @ExampleObject(
                      name = "Sample collection search response",
                      value =
                          "{\n"
                              + "  \"hits\": [\n"
                              + "    {\n"
                              + "      \"accession\": \"S-BSST1432\",\n"
                              + "      \"title\": \"Pharyngeal neuronal mechanisms...\",\n"
                              + "      \"releaseDate\": \"2124-05-21\",\n"
                              + "      \"collection\": \"bioimages\"\n"
                              + "    }\n"
                              + "  ],\n"
                              + "  \"totalHits\": 15,\n"
                              + "  \"page\": 1,\n"
                              + "  \"pageSize\": 20,\n"
                              + "  \"collection\": \"bioimages\",\n"
                              + "  \"sortBy\": \"releaseDate\",\n"
                              + "  \"sortOrder\": \"descending\"\n"
                              + "}"))),
          @ApiResponse(
              responseCode = "500",
              description = "Internal server error - collection registry not initialized",
              content = @Content)
      })
  @GetMapping(value = "/{collection}/search", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SearchResponseDTO> searchCollection(
      @Parameter(
          description =
              "Collection identifier to scope the search. Only submissions belonging to this collection will be returned.",
          example = "bioimages",
          required = true)
      @PathVariable
      String collection,
      @Parameter(
          description = "Free-text search query. Searches across all indexed text fields.",
          example = "Drosophila melanogaster")
      @RequestParam(defaultValue = "")
      String query,
      @Parameter(
          description = "1-based page number for pagination. Must be >= 1.",
          example = "1",
          schema = @Schema(minimum = "1", defaultValue = "1"))
      @RequestParam(defaultValue = "1")
      Integer page,
      @Parameter(
          description = "Number of results per page. Controls pagination size.",
          example = "20",
          schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
      @RequestParam(defaultValue = "20")
      Integer pageSize,
      @Parameter(
          description =
              "Field name to sort results by. Leave empty for relevance-based sorting. "
                  + "Common values: 'releaseDate', 'accession', 'title'.",
          example = "releaseDate")
      @RequestParam(defaultValue = "")
      String sortBy,
      @Parameter(
          description = "Sort direction. Either 'ascending' or 'descending'.",
          example = "descending",
          schema = @Schema(allowableValues = {"ascending", "descending"}))
      @RequestParam(defaultValue = "descending")
      String sortOrder,
      @Parameter(
          description =
              "Additional dynamic parameters for faceted filtering and field filtering:\n"
                  + "- **Facet filters**: Use `facet.<fieldname>=value` (e.g., `facet.organ=lung`)\n"
                  + "- **Field filters**: Use any registered field name (e.g., `title=cancer`)\n"
                  + "- Multi-value facets can use array notation: `facet.organism[]=human&facet.organism[]=mouse`",
          example = "facet.organ=lung&title=cancer")
      @RequestParam
      MultiValueMap<String, String> params) {

    // Prioritize query from params over explicit param (matches old behavior)
    String queryString = params.getFirst("query") != null ? params.getFirst("query") : query;

    SearchRequest searchRequest = new SearchRequest();
    searchRequest.setQuery(queryString);
    searchRequest.setCollection(collection);
    searchRequest.setPage(page);
    searchRequest.setPageSize(pageSize);
    searchRequest.setSortBy(sortBy);
    searchRequest.setSortOrder(sortOrder);

    populateFacetFields(params, searchRequest);

    // Call search service and get response
    SearchResponseDTO response = searchService.search(searchRequest);

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
            request.getFields().put(key, values.getFirst()); // Single-value fields
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
