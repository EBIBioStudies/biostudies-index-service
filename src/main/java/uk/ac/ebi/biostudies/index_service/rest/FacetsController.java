package uk.ac.ebi.biostudies.index_service.rest;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.Query;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.biostudies.index_service.search.FacetDimensionDTO;
import uk.ac.ebi.biostudies.index_service.search.query.FacetService;
import uk.ac.ebi.biostudies.index_service.search.query.LuceneQueryBuilder;

/**
 * REST controller for faceted search operations. Provides endpoints to retrieve facet information
 * for filtering submissions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class FacetsController {

  private static final String JSON_UNICODE_MEDIA_TYPE = "application/json;charset=UTF-8";
  private static final int DEFAULT_FACET_LIMIT = 10;

  private final FacetService facetService;
  private final LuceneQueryBuilder luceneQueryBuilder;

  public FacetsController(FacetService facetService, LuceneQueryBuilder luceneQueryBuilder) {
    this.facetService = facetService;
    this.luceneQueryBuilder = luceneQueryBuilder;
  }

  /**
   * Retrieves available facets for a specific collection. Facets can be filtered by an optional
   * search query and limited in size.
   *
   * @param collection the collection name (e.g., "public", "arrayexpress")
   * @param queryString optional search query to filter facet results
   * @param limit maximum number of facet values to return per dimension
   * @param allParams all request parameters (includes facets and fields)
   * @return list of facet dimensions with their values and hit counts
   */
  @GetMapping(value = "/{collection}/facets", produces = JSON_UNICODE_MEDIA_TYPE)
  public List<FacetDimensionDTO> getFacets(
      @PathVariable String collection,
      @RequestParam(value = "query", required = false, defaultValue = "") String queryString,
      @RequestParam(value = "limit", required = false, defaultValue = "" + DEFAULT_FACET_LIMIT)
          Integer limit,
      @RequestParam MultiValueMap<String, String> allParams) {

    // Separate facets from other fields
    Map<String, List<String>> selectedFacets = extractParameters(allParams, "facet.");
    Map<String, Object> selectedFields = extractFieldsAsObjects(allParams);

    // Build base query with field filters
    Query baseQuery =
        luceneQueryBuilder.buildQuery(queryString, collection, selectedFields).getQuery();

    // Get facets (FacetService applies security and facet drill-down)
    return facetService.getAllFacets(collection, baseQuery, limit, selectedFacets);
  }

  /**
   * Retrieves a specific facet dimension with its values and counts.
   *
   * @param collection the collection name (e.g., "public", "arrayexpress")
   * @param dimension the facet dimension name (e.g., "facet.organism", "facet.collection")
   * @param queryString optional search query to filter facet results
   * @param allParams all request parameters (includes facets and fields)
   * @return facet dimension with its values and hit counts, or null if dimension not found
   */
  @GetMapping(value = "/{collection}/facets/{dimension}", produces = JSON_UNICODE_MEDIA_TYPE)
  public FacetDimensionDTO getDimension(
      @PathVariable String collection,
      @PathVariable String dimension,
      @RequestParam(value = "query", required = false, defaultValue = "") String queryString,
      @RequestParam MultiValueMap<String, String> allParams) {

    // Separate facets from other fields
    Map<String, List<String>> selectedFacets = extractParameters(allParams, "facet.");
    Map<String, Object> selectedFields = extractFieldsAsObjects(allParams);

    // Build base query with field filters
    Query baseQuery =
        luceneQueryBuilder.buildQuery(queryString, collection, selectedFields).getQuery();

    // Get the specific dimension (FacetService applies security and facet drill-down)
    return facetService.getDimension(collection, dimension, baseQuery, selectedFacets);
  }

  /**
   * Extracts parameters that start with a given prefix.
   *
   * <p>Example: extractParameters(params, "facet.") Input:
   * facet.collection[]=bioimages&facet.collection[]=arrayexpress Output: {"facet.collection":
   * ["bioimages", "arrayexpress"]}
   */
  private Map<String, List<String>> extractParameters(
      MultiValueMap<String, String> allParams, String prefix) {

    Map<String, List<String>> result = new HashMap<>();

    for (Map.Entry<String, List<String>> entry : allParams.entrySet()) {
      String key = entry.getKey();

      if (key.startsWith(prefix)) {
        result.put(key, entry.getValue());
      }
    }

    return result;
  }

  /**
   * Extracts non-facet field parameters for query building. Converts to Map<String, Object> to
   * match LuceneQueryBuilder signature.
   *
   * <p>Example: author=Smith&title=cancer Output: {"author": "Smith", "title": "cancer"}
   */
  private Map<String, Object> extractFieldsAsObjects(MultiValueMap<String, String> allParams) {
    Map<String, Object> fields = new HashMap<>();

    for (Map.Entry<String, List<String>> entry : allParams.entrySet()) {
      String key = entry.getKey();

      // Skip facets and special parameters
      if (key.startsWith("facet.") || key.equals("query") || key.equals("limit")) {
        continue;
      }

      List<String> values = entry.getValue();

      // If single value, unwrap from list; otherwise keep as list
      if (values.size() == 1) {
        fields.put(key, values.get(0));
      } else {
        fields.put(key, values);
      }
    }

    return fields;
  }
}
