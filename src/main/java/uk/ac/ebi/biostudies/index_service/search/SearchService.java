package uk.ac.ebi.biostudies.index_service.search;

import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.facet.DrillDownQuery;
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
 * <p>This service coordinates the complete search workflow from query preprocessing through result
 * enhancement. It acts as the primary entry point for search operations and delegates specialized
 * tasks to focused components.
 *
 * <p><strong>Search Workflow:</strong>
 *
 * <ol>
 *   <li><strong>Preprocessing</strong> - Normalizes and validates search parameters
 *   <li><strong>Query Building</strong> - Constructs Lucene query with optional EFO expansion
 *   <li><strong>Security Filtering</strong> - Applies access control constraints
 *   <li><strong>Facet Filtering</strong> - Narrows results by selected facet values
 *   <li><strong>Query Execution</strong> - Executes paginated search against Lucene index
 *   <li><strong>Result Enhancement</strong> - Applies snippets, spell suggestions, term filtering,
 *       and facet formatting
 * </ol>
 *
 * <p>The service maintains separation of concerns by delegating result enhancement to {@link
 * SearchResponseProcessor}, which handles snippet extraction, spell checking, EFO term filtering,
 * and response formatting.
 *
 * @see SearchResponseProcessor
 * @see LuceneQueryBuilder
 * @see QueryPreprocessor
 */
@Slf4j
@Service
public class SearchService {

  private static final String RELEVANCE = "relevance";
  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final QueryPreprocessor preprocessor;
  private final LuceneQueryBuilder luceneQueryBuilder;
  private final SecurityQueryBuilder securityQueryBuilder;
  private final FacetService facetService;
  private final LuceneQueryExecutor queryExecutor;
  private final TaxonomyManager taxonomyManager;
  private final CollectionRegistryService collectionRegistryService;
  private final SearchResponseProcessor searchResponseProcessor;

  /**
   * Constructs the SearchService with all required dependencies.
   *
   * @param preprocessor validates and normalizes search requests
   * @param luceneQueryBuilder builds Lucene queries with optional EFO expansion
   * @param securityQueryBuilder applies access control filters to queries
   * @param facetService manages faceted search operations
   * @param queryExecutor executes Lucene queries and returns paginated results
   * @param taxonomyManager provides facet configuration
   * @param collectionRegistryService provides collection and property metadata
   * @param searchResponseProcessor enhances results with snippets, suggestions, and filtering
   */
  public SearchService(
      QueryPreprocessor preprocessor,
      LuceneQueryBuilder luceneQueryBuilder,
      SecurityQueryBuilder securityQueryBuilder,
      FacetService facetService,
      LuceneQueryExecutor queryExecutor,
      TaxonomyManager taxonomyManager,
      CollectionRegistryService collectionRegistryService,
      SearchResponseProcessor searchResponseProcessor) {
    this.preprocessor = preprocessor;
    this.luceneQueryBuilder = luceneQueryBuilder;
    this.securityQueryBuilder = securityQueryBuilder;
    this.facetService = facetService;
    this.queryExecutor = queryExecutor;
    this.taxonomyManager = taxonomyManager;
    this.collectionRegistryService = collectionRegistryService;
    this.searchResponseProcessor = searchResponseProcessor;
  }

  /**
   * Executes a complete search operation and returns enriched results.
   *
   * <p>This method orchestrates the entire search workflow:
   *
   * <ol>
   *   <li>Preprocesses the request (validation, defaults)
   *   <li>Builds the base Lucene query (with optional EFO expansion)
   *   <li>Applies security filters
   *   <li>Applies facet drill-down filters
   *   <li>Executes the query against the submission index
   *   <li>Enhances results (snippets, suggestions, term filtering, facets)
   * </ol>
   *
   * <p><strong>Result Enhancement:</strong> The {@link SearchResponseProcessor} automatically
   * applies:
   *
   * <ul>
   *   <li>Content snippet extraction with query highlighting
   *   <li>Spelling suggestions for poor results (≤5 hits)
   *   <li>EFO/synonym term filtering (shows only terms present in index)
   *   <li>Facet formatting for UI display
   * </ul>
   *
   * @param rawRequest the raw search request from the user
   * @return enriched search response with hits, facets, suggestions, and filtered expansion terms
   */
  public SearchResponseDTO search(SearchRequest rawRequest) {
    log.info("Executing search: {}", rawRequest);

    try {
      // 1. Preprocess request: validate, apply defaults for sort/pagination/collection
      SearchRequest request = preprocessor.preprocess(rawRequest);
      log.debug("Preprocessed request: {}", request);

      // 2. Build base Lucene query: parse query string, apply EFO expansion if applicable
      QueryResult queryResult =
          luceneQueryBuilder.buildQuery(
              request.getQuery(), request.getCollection(), request.getFields());
      log.debug("Base query built: {}", queryResult.getQuery());

      // 3. Apply security filters: restrict results based on user permissions and visibility
      Query secureQuery = securityQueryBuilder.applySecurity(queryResult.getQuery());
      log.debug("Security filters applied: {}", secureQuery);

      // 4. Apply facet drill-down filters: narrow results by selected facet values
      DrillDownQuery drillDownQuery = applyFacetFilters(secureQuery, request.getFacets());
      log.debug("Facet filters applied: {}", drillDownQuery);

      // 5. Execute search: run query against submission index with pagination and sorting
      int page = request.getPage() != null ? request.getPage() : DEFAULT_PAGE;
      int pageSize = request.getPageSize() != null ? request.getPageSize() : DEFAULT_PAGE_SIZE;
      Sort sort = buildSort(request.getSortBy(), request.getSortOrder());

      PaginatedResult paginatedResult =
          queryExecutor.execute(IndexName.SUBMISSION, drillDownQuery, page, pageSize, sort);
      log.info(
          "Search completed: {} total hits, returning page {}/{} ({} hits)",
          paginatedResult.totalHits(),
          page,
          pageSize,
          paginatedResult.hits().size());

      // 6. Enhance results: apply snippets, spell suggestions, term filtering, and facet
      // formatting
      // This delegates to SearchResponseProcessor which handles:
      // - Content snippet extraction with highlighting
      // - Spelling suggestions (if ≤5 hits)
      // - EFO/synonym term filtering (show only terms in index)
      // - Facet formatting for UI
      return searchResponseProcessor.buildEnrichedResponse(
          request, paginatedResult, request.getQuery(), queryResult, drillDownQuery);

    } catch (IOException ex) {
      log.error("IO error during search execution", ex);
      return searchResponseProcessor.buildErrorResponse(
          rawRequest, "Search failed due to IO error");
    } catch (Exception ex) {
      log.error("Unexpected error during search execution", ex);
      return searchResponseProcessor.buildErrorResponse(
          rawRequest, "Search failed: " + ex.getMessage());
    }
  }

  /**
   * Applies facet drill-down filters to narrow search results.
   *
   * <p>Converts user-selected facet filters into a Lucene {@link DrillDownQuery} that restricts
   * results to documents matching all selected facet values.
   *
   * <p><strong>Example:</strong> If user selects "Organism: Human" and "Year: 2024", only documents
   * tagged with both values will match.
   *
   * @param baseQuery the base query before facet filtering
   * @param selectedFacets map of facet dimension names to selected values (e.g., {"organism":
   *     ["human"], "year": ["2024"]})
   * @return drill-down query with facet filters applied, or base query wrapped if no filters
   */
  private DrillDownQuery applyFacetFilters(
      Query baseQuery, Map<String, List<String>> selectedFacets) {
    if (selectedFacets == null || selectedFacets.isEmpty()) {
      // No filters: wrap base query in DrillDownQuery for consistent handling
      return new DrillDownQuery(taxonomyManager.getFacetsConfig(), baseQuery);
    }

    // Convert facet dimension names (strings) to PropertyDescriptors for validation
    Map<PropertyDescriptor, List<String>> facetsByProperty = convertToPropertyMap(selectedFacets);

    // Apply facet filters to create drill-down query
    return facetService.addFacetDrillDownFilters(
        taxonomyManager.getFacetsConfig(), baseQuery, facetsByProperty);
  }

  /**
   * Builds a Lucene Sort object from request parameters.
   *
   * <p>The {@link QueryPreprocessor} has already normalized sortBy and sortOrder based on search
   * context. This method translates those values into Lucene's native sorting constructs.
   *
   * <p><strong>Supported sort fields:</strong>
   *
   * <ul>
   *   <li><strong>relevance</strong> - Sort by query score (default for keyword searches)
   *   <li><strong>releaseDate</strong> - Sort by release date (default for browsing)
   *   <li><strong>modificationDate</strong> - Sort by last modification
   *   <li><strong>accession</strong> - Sort by accession number
   *   <li><strong>title</strong> - Sort alphabetically by title
   *   <li><strong>fileCount</strong> - Sort by number of files
   * </ul>
   *
   * <p><strong>Secondary sort:</strong> All non-relevance sorts include relevance as a tie-breaker
   * for consistent ordering of equal values.
   *
   * @param sortBy field to sort by (already normalized by preprocessor)
   * @param sortOrder sort order ("ascending" or "descending")
   * @return Lucene Sort object, or null for pure relevance sorting
   */
  private Sort buildSort(String sortBy, String sortOrder) {
    // Relevance sorting: return null to use Lucene's default score-based sorting
    if (sortBy == null || RELEVANCE.equalsIgnoreCase(sortBy)) {
      return null;
    }

    // Determine sort direction (descending = reverse in Lucene terms)
    boolean reverse = "descending".equalsIgnoreCase(sortOrder);

    // Create primary sort field
    SortField sortField = createSortField(sortBy, reverse);

    // Add relevance as secondary sort for consistent tie-breaking
    return new Sort(sortField, SortField.FIELD_SCORE);
  }

  /**
   * Creates a Lucene SortField for a given field name.
   *
   * <p>Maps user-facing field names to actual indexed field names and determines the appropriate
   * Lucene sort type (LONG for dates, STRING for text, INT for counts).
   *
   * <p><strong>Field mappings:</strong>
   *
   * <ul>
   *   <li>Date fields use LONG type (stored as epoch milliseconds with DocValues)
   *   <li>String fields use sortable versions with SortedDocValues (e.g., "title.sort")
   *   <li>Numeric fields use appropriate numeric types (INT, LONG)
   * </ul>
   *
   * @param fieldName the user-facing field name to sort by
   * @param reverse true for descending order, false for ascending
   * @return configured SortField with appropriate type and direction
   */
  private SortField createSortField(String fieldName, boolean reverse) {
    return switch (fieldName.toLowerCase()) {
      // Date fields: stored as long epoch milliseconds with DocValues
      case "releasedate", "release_date" ->
          new SortField("releaseDate", SortField.Type.LONG, reverse);
      case "modificationdate", "modification_date", "modificationtime" ->
          new SortField("modificationDate", SortField.Type.LONG, reverse);

      // String fields: use sortable versions with SortedDocValues
      case "accession", "accno" -> new SortField("accession", SortField.Type.STRING, reverse);
      case "title" ->
          new SortField("title.sort", SortField.Type.STRING, reverse); // Keyword field for sorting

      // Numeric fields: use appropriate numeric type
      case "filecount", "file_count" -> new SortField("fileCount", SortField.Type.INT, reverse);

      // Unknown field: attempt STRING sort with warning
      default -> {
        log.warn("Unknown sort field '{}', attempting STRING sort", fieldName);
        yield new SortField(fieldName, SortField.Type.STRING, reverse);
      }
    };
  }

  /**
   * Converts facet dimension names to validated PropertyDescriptors.
   *
   * <p>Validates that requested facet dimensions exist in the collection registry and are
   * configured as facetable properties. Invalid dimensions are ignored with a debug log.
   *
   * <p>This prevents users from attempting to filter on non-faceted fields or non-existent
   * dimensions.
   *
   * @param facetsByName map of user-provided facet dimension names to selected values
   * @return map of validated PropertyDescriptors to values, excluding invalid dimensions
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

      // Validate property exists and is configured as a facet
      if (property != null && property.isFacet()) {
        result.put(property, entry.getValue());
      } else {
        log.debug(
            "Ignoring invalid facet dimension '{}' (not found or not facetable)", dimensionName);
      }
    }

    return result;
  }
}
