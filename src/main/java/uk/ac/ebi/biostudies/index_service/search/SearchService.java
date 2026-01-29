package uk.ac.ebi.biostudies.index_service.search;

import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;
import uk.ac.ebi.biostudies.index_service.search.engine.LuceneQueryExecutor;
import uk.ac.ebi.biostudies.index_service.search.engine.PaginatedResult;
import uk.ac.ebi.biostudies.index_service.search.preprocessing.QueryPreprocessor;
import uk.ac.ebi.biostudies.index_service.search.query.FacetService;
import uk.ac.ebi.biostudies.index_service.search.query.LuceneQueryBuilder;
import uk.ac.ebi.biostudies.index_service.search.query.QueryResult;
import uk.ac.ebi.biostudies.index_service.search.security.SecurityQueryBuilder;

/**
 * High-level search orchestration service.
 *
 * <p>Coordinates query parsing, security filtering, faceted search, and result formatting. This
 * service provides the main entry point for executing searches and retrieving faceted results for
 * UI display.
 */
@Slf4j
@Service
public class SearchService {

  private static final String PUBLIC_COLLECTION = "public";
  private static final String COLLECTION_FACET = "collection";
  private static final int DEFAULT_FACET_LIMIT = 20;
  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final QueryPreprocessor preprocessor;
  private final LuceneQueryBuilder luceneQueryBuilder;
  private final SecurityQueryBuilder securityQueryBuilder;
  private final FacetService facetService;
  private final LuceneQueryExecutor queryExecutor;
  private final TaxonomyManager taxonomyManager;
  private final CollectionRegistryService collectionRegistryService;

  public SearchService(
      QueryPreprocessor preprocessor,
      LuceneQueryBuilder luceneQueryBuilder,
      SecurityQueryBuilder securityQueryBuilder,
      FacetService facetService,
      LuceneQueryExecutor queryExecutor,
      TaxonomyManager taxonomyManager,
      CollectionRegistryService collectionRegistryService) {
    this.preprocessor = preprocessor;
    this.luceneQueryBuilder = luceneQueryBuilder;
    this.securityQueryBuilder = securityQueryBuilder;
    this.facetService = facetService;
    this.queryExecutor = queryExecutor;
    this.taxonomyManager = taxonomyManager;
    this.collectionRegistryService = collectionRegistryService;
  }

  /**
   * Executes a complete search with facets.
   *
   * @param rawRequest the search request containing query, collection, and filters
   * @return search response with hits and facets
   */
  public SearchResponseDTO search(SearchRequest rawRequest) {
    log.info("Searching for {}", rawRequest);

    try {
      // 1. Preprocess request
      SearchRequest request = preprocessor.preprocess(rawRequest);
      log.debug("Preprocessed request: {}", request);

      // 2. Build base Lucene query
      QueryResult queryResult =
          luceneQueryBuilder.buildQuery(
              request.getQuery(), request.getCollection(), request.getFields());
      log.debug("Base query: {}", queryResult.getQuery());

      // 3. Apply security filters
      Query secureQuery = securityQueryBuilder.applySecurity(queryResult.getQuery());
      log.debug("Secure query: {}", secureQuery);

      // 4. Apply facet filters (drill-down)
      DrillDownQuery drillDownQuery = applyFacetFilters(secureQuery, request.getFacets());
      log.debug("Drill-down query: {}", drillDownQuery);

      // 5. Execute search using LuceneQueryExecutor
      int page = request.getPage() != null ? request.getPage() : DEFAULT_PAGE;
      int pageSize = request.getPageSize() != null ? request.getPageSize() : DEFAULT_PAGE_SIZE;
      Sort sort = buildSort(request.getSortBy(), request.getSortOrder());

      PaginatedResult paginatedResult =
          queryExecutor.execute(IndexName.SUBMISSION, drillDownQuery, page, pageSize, sort);
      log.debug(
          "Search executed: {} total hits, page {}/{}",
          paginatedResult.totalHits(),
          page,
          pageSize);

      // 6. Get facets for UI
      int facetLimit = request.getFacetLimit() != null ? request.getFacetLimit() : DEFAULT_FACET_LIMIT;
      List<FacetDimensionDTO> facets =
          getFacetsForUI(request.getCollection(), drillDownQuery, facetLimit, request.getFacets());

      // 7. Convert hits to DTOs
      List<HitDTO> hits = convertToHitDTOs(paginatedResult);

      // 8. Build response
      return buildSearchResponse(request, paginatedResult, facets, hits);

    } catch (IOException ex) {
      log.error("IO error during search", ex);
      return buildErrorResponse(rawRequest, "Search failed due to IO error");
    } catch (Exception ex) {
      log.error("Unexpected error during search", ex);
      return buildErrorResponse(rawRequest, "Search failed: " + ex.getMessage());
    }
  }

  /**
   * Gets formatted facet dimensions for UI display.
   *
   * <p>This is equivalent to the old {@code getDefaultFacetTemplate} method. It retrieves facet
   * counts and formats them according to UI requirements, handling special cases like collection
   * visibility, N/A values, and release year sorting.
   *
   * @param collectionName the collection to search within
   * @param queryString the user's query string
   * @param limit maximum number of facet values per dimension
   * @param selectedFacets currently selected facet filters
   * @return list of formatted facet dimensions for UI display
   */
  public List<FacetDimensionDTO> getFacetTemplate(
      String collectionName,
      String queryString,
      int limit,
      Map<String, List<String>> selectedFacets) {

    try {
      // 1. Build base query
      QueryResult queryResult =
          luceneQueryBuilder.buildQuery(queryString, collectionName, Collections.emptyMap());

      // 2. Apply security
      Query secureQuery = securityQueryBuilder.applySecurity(queryResult.getQuery());

      // 3. Apply facet filters
      DrillDownQuery drillDownQuery = applyFacetFilters(secureQuery, selectedFacets);

      // 4. Get facets
      return getFacetsForUI(collectionName, drillDownQuery, limit, selectedFacets);

    } catch (Exception ex) {
      log.error("Error getting facet template for collection {}", collectionName, ex);
      return Collections.emptyList();
    }
  }

  /**
   * Applies facet filters to create a drill-down query.
   *
   * @param baseQuery the base query before facet filtering
   * @param selectedFacets map of facet dimension names to selected values
   * @return drill-down query with facet filters applied
   */
  private DrillDownQuery applyFacetFilters(
      Query baseQuery, Map<String, List<String>> selectedFacets) {
    if (selectedFacets == null || selectedFacets.isEmpty()) {
      return new DrillDownQuery(taxonomyManager.getFacetsConfig(), baseQuery);
    }

    // Convert facet names to PropertyDescriptors
    Map<PropertyDescriptor, List<String>> facetsByProperty = convertToPropertyMap(selectedFacets);

    // Apply facet filters
    return facetService.addFacetDrillDownFilters(
        taxonomyManager.getFacetsConfig(), baseQuery, facetsByProperty);
  }

  /**
   * Builds Lucene Sort from request parameters.
   *
   * @param sortBy field to sort by
   * @param sortOrder sort order ("ascending" or "descending")
   * @return Lucene Sort object, or null for relevance sorting
   */
  private Sort buildSort(String sortBy, String sortOrder) {
    // TODO: Implement sort building based on sortBy and sortOrder
    // For now, return null for relevance sorting
    if (sortBy == null || "relevance".equalsIgnoreCase(sortBy)) {
      return null;
    }

    // Implement actual sort building here
    log.warn("Sort building not yet implemented for sortBy={}, sortOrder={}", sortBy, sortOrder);
    return null;
  }

  /**
   * Gets formatted facets for UI display.
   *
   * @param collectionName the collection name
   * @param drillDownQuery the query with facet filters
   * @param limit max facet values per dimension
   * @param selectedFacets currently selected facets
   * @return list of formatted facet dimensions
   */
  private List<FacetDimensionDTO> getFacetsForUI(
      String collectionName,
      DrillDownQuery drillDownQuery,
      int limit,
      Map<String, List<String>> selectedFacets) {

    Map<String, Map<String, Integer>> selectedFacetFreq = new HashMap<>();

    // Get facet results from service
    List<FacetResult> facetResults =
        facetService.getFacetsForQuery(drillDownQuery, limit, selectedFacetFreq, selectedFacets);

    // Format for UI
    return formatFacetsForUI(facetResults, selectedFacetFreq, collectionName, limit);
  }

  /**
   * Formats facet results for UI display.
   *
   * <p>Handles special cases:
   *
   * <ul>
   *   <li>Collection facet visibility (only shown for public collection or collections with
   *       sub-collections)
   *   <li>N/A value filtering based on property configuration
   *   <li>Release year sorting (descending, N/A removed)
   *   <li>Selected low-frequency facets inclusion
   * </ul>
   *
   * @param facetResults raw facet results from Lucene
   * @param selectedFacetFreq frequencies of selected facets
   * @param collectionName the collection being searched
   * @param limit max values per dimension
   * @return formatted facet dimensions for UI
   */
  private List<FacetDimensionDTO> formatFacetsForUI(
      List<FacetResult> facetResults,
      Map<String, Map<String, Integer>> selectedFacetFreq,
      String collectionName,
      int limit) {

    List<FacetDimensionDTO> result = new ArrayList<>();
    CollectionRegistry registry = collectionRegistryService.getCurrentRegistry();

    for (FacetResult facetResult : facetResults) {
      if (facetResult == null) {
        continue;
      }

      String dimensionName = facetResult.dim;
      PropertyDescriptor property = registry.getPropertyDescriptor(dimensionName);

      if (property == null || !property.isFacet()) {
        continue;
      }

      // Special case: hide collection facet for non-public collections without sub-collections
      if (shouldHideCollectionFacet(dimensionName, collectionName)) {
        continue;
      }

      // Build facet dimension
      FacetDimensionDTO dimension = new FacetDimensionDTO();
      dimension.setName(dimensionName);
      dimension.setTitle(property.getTitle() != null ? property.getTitle() : dimensionName);

      if (property.getFacetType() != null) {
        dimension.setType(property.getFacetType());
      }

      // Build children (facet values)
      List<FacetValueDTO> children = new ArrayList<>();

      // Add selected facets first (may be low-frequency)
      if (selectedFacetFreq.containsKey(dimensionName)) {
        Map<String, Integer> freqMap = selectedFacetFreq.get(dimensionName);
        for (Map.Entry<String, Integer> entry : freqMap.entrySet()) {
          FacetValueDTO value = new FacetValueDTO();
          value.setName(entry.getKey());
          value.setValue(entry.getKey());
          value.setHits(entry.getValue());
          children.add(value);
        }
      }

      // Add top facet values
      if (facetResult.labelValues != null) {
        boolean hideNA = shouldHideNA(property);
        String naValue = property.getDefaultValue() != null ? property.getDefaultValue() : "N/A";

        for (LabelAndValue labelAndValue : facetResult.labelValues) {
          // Skip N/A if configured
          if (hideNA && labelAndValue.label.equalsIgnoreCase(naValue)) {
            continue;
          }

          // Skip if already added as selected facet
          if (selectedFacetFreq.containsKey(dimensionName)
              && selectedFacetFreq.get(dimensionName).containsKey(labelAndValue.label)) {
            continue;
          }

          FacetValueDTO value = new FacetValueDTO();
          value.setName(labelAndValue.label);
          value.setValue(labelAndValue.label);
          value.setHits(labelAndValue.value.intValue());
          children.add(value);
        }
      }

      // Sort children by name
      children.sort(Comparator.comparing(FacetValueDTO::getName));

      // Special handling for release_year: reverse sort and remove N/A
      if (FacetService.RELEASED_YEAR_FACET.equalsIgnoreCase(dimensionName)) {
        Collections.reverse(children);
        children.removeIf(v -> "N/A".equalsIgnoreCase(v.getName()));

        // Apply limit for release year
        if (children.size() > limit) {
          children = children.subList(0, limit);
        }
      }

      dimension.setChildren(children);
      result.add(dimension);
    }

    return result;
  }

  /**
   * Determines if collection facet should be hidden.
   *
   * @param dimensionName the facet dimension name
   * @param collectionName the current collection
   * @return true if collection facet should be hidden
   */
  private boolean shouldHideCollectionFacet(String dimensionName, String collectionName) {
    if (!COLLECTION_FACET.equalsIgnoreCase(dimensionName)) {
      return false;
    }

    // Show collection facet for public collection
    if (PUBLIC_COLLECTION.equalsIgnoreCase(collectionName)) {
      return false;
    }

    // TODO: Check if collection has sub-collections
    // For now, hide for non-public collections
    return true;
  }

  /**
   * Determines if N/A values should be hidden for a property.
   *
   * @param property the property descriptor
   * @return true if N/A should be hidden
   */
  private boolean shouldHideNA(PropertyDescriptor property) {
    return property.getNaVisible() != null && !property.getNaVisible();
  }

  /**
   * Converts facet names to PropertyDescriptors.
   *
   * @param facetsByName map of facet names to values
   * @return map of PropertyDescriptors to values
   */
  private Map<PropertyDescriptor, List<String>> convertToPropertyMap(
      Map<String, List<String>> facetsByName) {

    if (facetsByName == null || facetsByName.isEmpty()) {
      return Collections.emptyMap();
    }

    CollectionRegistry registry = collectionRegistryService.getCurrentRegistry();
    Map<PropertyDescriptor, List<String>> result = new HashMap<>();

    for (Map.Entry<String, List<String>> entry : facetsByName.entrySet()) {
      String dimensionName = entry.getKey();
      PropertyDescriptor property = registry.getPropertyDescriptor(dimensionName);

      if (property != null && property.isFacet()) {
        result.put(property, entry.getValue());
      } else {
        log.debug("Ignoring invalid facet dimension: {}", dimensionName);
      }
    }

    return result;
  }

  /**
   * Converts paginated result to hit DTOs.
   *
   * @param paginatedResult the paginated search result
   * @return list of hit DTOs
   */
  private List<HitDTO> convertToHitDTOs(PaginatedResult paginatedResult) {
    // TODO: Implement conversion from ScoreDoc to HitDTO
    // This will require accessing the index searcher to retrieve documents
    log.warn("Hit conversion not yet implemented, returning empty list");
    return Collections.emptyList();
  }

  /**
   * Builds search response DTO.
   *
   * @param request the search request
   * @param paginatedResult the paginated search result
   * @param facets the formatted facets
   * @param hits the converted hit DTOs
   * @return complete search response
   */
  private SearchResponseDTO buildSearchResponse(
      SearchRequest request,
      PaginatedResult paginatedResult,
      List<FacetDimensionDTO> facets,
      List<HitDTO> hits) {

    return new SearchResponseDTO(
        paginatedResult.page(),
        paginatedResult.pageSize(),
        paginatedResult.totalHits(),
        true, // isTotalHitsExact
        request.getSortBy() != null ? request.getSortBy() : "relevance",
        request.getSortOrder() != null ? request.getSortOrder() : "descending",
        List.of(), // suggestions - TODO
        List.of(), // expandedEfoTerms - TODO
        List.of(), // expandedSynonyms - TODO
        request.getQuery(),
        request.getFacets() != null ? request.getFacets() : Collections.emptyMap(),
        facets,
        hits);
  }

  /**
   * Builds error response.
   *
   * @param request the failed request
   * @param errorMessage error message
   * @return error search response
   */
  private SearchResponseDTO buildErrorResponse(SearchRequest request, String errorMessage) {
    log.error("Building error response: {}", errorMessage);

    return new SearchResponseDTO(
        1,
        20,
        0L,
        true,
        "relevance",
        "descending",
        List.of(),
        List.of(),
        List.of(),
        request.getQuery(),
        Collections.emptyMap(),
        Collections.emptyList(),
        Collections.emptyList());
  }
}
