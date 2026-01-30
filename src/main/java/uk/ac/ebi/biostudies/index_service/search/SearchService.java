package uk.ac.ebi.biostudies.index_service.search;

import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
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
  private static final String RELEVANCE = "relevance";
  private static final String RELEASE_DATE = "releaseDate";
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
  private final SearchSnippetExtractor snippetExtractor;

  public SearchService(
      QueryPreprocessor preprocessor,
      LuceneQueryBuilder luceneQueryBuilder,
      SecurityQueryBuilder securityQueryBuilder,
      FacetService facetService,
      LuceneQueryExecutor queryExecutor,
      TaxonomyManager taxonomyManager,
      CollectionRegistryService collectionRegistryService,
      SearchSnippetExtractor snippetExtractor) {
    this.preprocessor = preprocessor;
    this.luceneQueryBuilder = luceneQueryBuilder;
    this.securityQueryBuilder = securityQueryBuilder;
    this.facetService = facetService;
    this.queryExecutor = queryExecutor;
    this.taxonomyManager = taxonomyManager;
    this.collectionRegistryService = collectionRegistryService;
    this.snippetExtractor = snippetExtractor;
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
      // 1. Preprocess request (applies defaults for sort, pagination, etc.)
      SearchRequest request = preprocessor.preprocess(rawRequest);
      log.debug("Preprocessed request: {}", request);

      // 2. Build base Lucene query (may include EFO expansion)
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
      int facetLimit =
          request.getFacetLimit() != null ? request.getFacetLimit() : DEFAULT_FACET_LIMIT;
      List<FacetDimensionDTO> facets =
          getFacetsForUI(request.getCollection(), drillDownQuery, facetLimit, request.getFacets());

      // 7. Get hits from paginated result
      List<SearchHit> hits = paginatedResult.hits();

      // 7.5. Apply snippet extraction to content field
      List<SearchHit> hitsWithSnippets = extractSnippets(hits, queryResult.getQuery());
      log.debug("Extracted snippets for {} hits", hitsWithSnippets.size());

      // 8. Build response
      return buildSearchResponse(request, queryResult, paginatedResult, facets, hitsWithSnippets);

    } catch (IOException ex) {
      log.error("IO error during search", ex);
      return buildErrorResponse(rawRequest, "Search failed due to IO error");
    } catch (Exception ex) {
      log.error("Unexpected error during search", ex);
      return buildErrorResponse(rawRequest, "Search failed: " + ex.getMessage());
    }
  }

  /**
   * Extracts relevant snippets from the content field of each search hit.
   *
   * @param hits the original search hits with full content
   * @param query the query to use for snippet extraction
   * @return new list of search hits with content replaced by snippets
   */
  private List<SearchHit> extractSnippets(List<SearchHit> hits, Query query) {
    return hits.stream().map(hit -> extractSnippet(hit, query)).toList();
  }

  /**
   * Extracts a snippet from a single search hit's content field.
   *
   * @param hit the original search hit
   * @param query the query to use for snippet extraction
   * @return a new SearchHit with content replaced by a snippet
   */
  private SearchHit extractSnippet(SearchHit hit, Query query) {
    String snippet =
        snippetExtractor.extractSnippet(
            query,
            "content", // field name
            hit.content(),
            true // fragmentOnly
            );

    // Create new SearchHit with snippet instead of full content
    return new SearchHit(
        hit.accession(),
        hit.type(),
        hit.title(),
        hit.author(),
        hit.links(),
        hit.files(),
        hit.releaseDate(),
        hit.views(),
        hit.isPublic(),
        snippet // replaced content
        );
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
   * <p>The preprocessor has already determined the sortBy and sortOrder values based on query
   * context. This method translates them into a Lucene Sort object.
   *
   * <p>Supported sort fields:
   *
   * <ul>
   *   <li>"relevance" - Sort by relevance score (default for searches)
   *   <li>"releaseDate" - Sort by release date (default for browsing)
   *   <li>Other sortable fields as configured in registry
   * </ul>
   *
   * @param sortBy field to sort by (already normalized by preprocessor)
   * @param sortOrder sort order ("ascending" or "descending")
   * @return Lucene Sort object, or null for relevance sorting
   */
  private Sort buildSort(String sortBy, String sortOrder) {
    // Relevance sorting (null = use query score)
    if (sortBy == null || RELEVANCE.equalsIgnoreCase(sortBy)) {
      return null;
    }

    // Determine if reverse (descending = reverse in Lucene)
    boolean reverse = "descending".equalsIgnoreCase(sortOrder);

    // Build sort field based on known sortable fields
    SortField sortField = createSortField(sortBy, reverse);

    // Always add relevance as secondary sort for tie-breaking
    return new Sort(sortField, SortField.FIELD_SCORE);
  }

  /**
   * Creates a SortField for a given field name.
   *
   * <p>Maps user-facing field names to actual indexed field names and determines the appropriate
   * Lucene sort type.
   *
   * @param fieldName the field to sort by
   * @param reverse true for descending order
   * @return configured SortField
   */
  private SortField createSortField(String fieldName, boolean reverse) {
    return switch (fieldName.toLowerCase()) {
      // Date fields (stored as long epoch millis with DocValues)
      case "releasedate", "release_date" ->
          new SortField("releaseDate", SortField.Type.LONG, reverse);
      case "modificationdate", "modification_date", "modificationtime" ->
          new SortField("modificationDate", SortField.Type.LONG, reverse);

      // String fields (use sortable versions with SortedDocValues)
      case "accession", "accno" -> new SortField("accession", SortField.Type.STRING, reverse);
      case "title" -> new SortField("title.sort", SortField.Type.STRING, reverse);

      // Numeric fields
      case "filecount", "file_count" -> new SortField("fileCount", SortField.Type.INT, reverse);

      // Default: try as string
      default -> {
        log.warn("Unknown sort field '{}', attempting STRING sort", fieldName);
        yield new SortField(fieldName, SortField.Type.STRING, reverse);
      }
    };
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
   * Builds search response DTO.
   *
   * <p>Matches old behavior:
   *
   * <ul>
   *   <li>Query is null when highlighting disabled (browsing mode)
   *   <li>Facets are null when empty
   *   <li>Includes EFO expansion terms if available
   * </ul>
   *
   * @param request the search request
   * @param queryResult the query result with potential expansion terms
   * @param paginatedResult the paginated search result
   * @param facets the formatted facets
   * @param hits the converted hit DTOs
   * @return complete search response
   */
  private SearchResponseDTO buildSearchResponse(
      SearchRequest request,
      QueryResult queryResult,
      PaginatedResult paginatedResult,
      List<FacetDimensionDTO> facets,
      List<SearchHit> hits) {

    // Match old behavior: query is null when highlighting disabled
    String displayQuery = request.isHighlightingEnabled() ? request.getQuery() : null;

    // Match old behavior: facets null when empty
    Map<String, List<String>> displayFacets =
        (request.getFacets() == null || request.getFacets().isEmpty()) ? null : request.getFacets();

    return new SearchResponseDTO(
        paginatedResult.page(),
        paginatedResult.pageSize(),
        paginatedResult.totalHits(),
        true, // isTotalHitsExact
        request.getSortBy() != null ? request.getSortBy() : RELEVANCE,
        request.getSortOrder() != null ? request.getSortOrder() : "descending",
        List.of(), // suggestions - TODO
        queryResult.getExpandedEfoTerms(),
        queryResult.getExpandedSynonyms(),
        displayQuery, // null when browsing
        displayFacets, // null when empty
        //facets,
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

    // Match old behavior: "*:*" becomes null
    String displayQuery = "*:*".equals(request.getQuery()) ? null : request.getQuery();

    return new SearchResponseDTO(
        1,
        20,
        0L,
        true,
        RELEVANCE,
        "descending",
        List.of(),
        Set.of(), // expandedEfoTerms
        Set.of(), // expandedSynonyms
        displayQuery,
        null, // facets
        //Collections.emptyList(), // facetDimensions
        Collections.emptyList()); // hits
  }
}
