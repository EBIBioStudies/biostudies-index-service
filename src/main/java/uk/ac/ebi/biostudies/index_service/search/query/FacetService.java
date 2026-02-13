package uk.ac.ebi.biostudies.index_service.search.query;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;
import uk.ac.ebi.biostudies.index_service.search.FacetDimensionDTO;
import uk.ac.ebi.biostudies.index_service.search.FacetValueDTO;
import uk.ac.ebi.biostudies.index_service.search.security.SecurityQueryBuilder;

/**
 * Service for applying faceted search filtering to Lucene queries.
 *
 * <p>This service creates drill-down queries that allow users to refine search results by selecting
 * specific facet values (dimensions). It integrates with Apache Lucene's faceted search
 * capabilities to provide hierarchical filtering on indexed collections.
 *
 * <p>Facet information is stored directly in the main index using SortedSetDocValuesFacetField.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Building drill-down queries from selected facet values
 *   <li>Counting facet values for filtered result sets
 *   <li>Handling collection-specific facet visibility rules
 *   <li>Managing special facet behaviors (e.g., release year sorting, NA visibility)
 * </ul>
 */
@Slf4j
@Service
public class FacetService {

  /** Name of the release year facet dimension, which receives special sorting treatment */
  public static final String RELEASED_YEAR_FACET = "facet.releasedyear";

  private static final int MAX_FACET_LIMIT = 10_000;
  private final IndexManager indexManager;
  private final TaxonomyManager taxonomyManager;
  private final CollectionRegistryService collectionRegistryService;
  private final SecurityQueryBuilder securityQueryBuilder;

  public FacetService(
      IndexManager indexManager,
      TaxonomyManager taxonomyManager,
      CollectionRegistryService collectionRegistryService,
      SecurityQueryBuilder securityQueryBuilder) {
    this.indexManager = indexManager;
    this.taxonomyManager = taxonomyManager;
    this.collectionRegistryService = collectionRegistryService;
    this.securityQueryBuilder = securityQueryBuilder;
  }

  /**
   * Constructs a drill-down query by applying facet filters to a base query.
   *
   * <p>Drill-down filtering restricts results to documents that match ALL selected facet values
   * within each dimension (AND logic across dimensions, OR logic within a dimension if multiple
   * values are selected).
   *
   * @param facetsConfig the Lucene facets configuration
   * @param primaryQuery the base search query
   * @param selectedFacetValues map of property descriptors to their selected values
   * @return a DrillDownQuery with facet filters applied
   */
  public DrillDownQuery addFacetDrillDownFilters(
      FacetsConfig facetsConfig,
      Query primaryQuery,
      Map<PropertyDescriptor, List<String>> selectedFacetValues) {

    DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, primaryQuery);

    for (Map.Entry<PropertyDescriptor, List<String>> entry : selectedFacetValues.entrySet()) {
      PropertyDescriptor property = entry.getKey();
      List<String> values = entry.getValue();

      // Skip invalid or non-facet properties
      if (property == null || !property.isFacet() || values == null || values.isEmpty()) {
        continue;
      }

      String dimensionName = property.getName();

      // Add each value as a drill-down filter, applying case transformation if configured
      for (String value : values) {
        String processedValue = property.isToLowerCase() ? value.toLowerCase() : value;
        drillDownQuery.add(dimensionName, processedValue);
      }
    }

    return drillDownQuery;
  }

  /**
   * Retrieves facet results for a drill-down query.
   *
   * <p>Executes the query with all filters applied, producing dependent facet counts. This means
   * facet counts reflect the current filter selection - facets are refined based on what documents
   * match the drilldown query.
   *
   * <p><b>Important:</b> Private facets are excluded for unauthorized users. Release year facet
   * returns unlimited results while others respect the limit.
   *
   * @param drillDownQuery the drill-down query with applied facet filters
   * @param limit maximum number of top children to retrieve per dimension (ignored for
   *     release_year)
   * @param selectedFacetFreq map to populate with frequencies of selected facets that may not
   *     appear in top results due to low counts
   * @param selectedFacets map of currently selected facet dimensions to their values
   * @return list of facet results for all configured dimensions, never null (may be empty)
   */
  public List<FacetResult> getFacetsForQuery(
      DrillDownQuery drillDownQuery,
      int limit,
      Map<String, Map<String, Integer>> selectedFacetFreq,
      Map<String, List<String>> selectedFacets) {

    List<FacetResult> allResults = new ArrayList<>();
    IndexSearcher indexSearcher = null;

    try {
      // Acquire main index searcher
      indexSearcher = indexManager.acquireSearcher(IndexName.SUBMISSION);

      // Create collector for facets
      FacetsCollector facetsCollector = new FacetsCollector();

      // Execute search with ALL filters applied
      indexSearcher.search(drillDownQuery, facetsCollector);

      // Build state for sorted set doc values facets
      FacetsConfig facetsConfig = taxonomyManager.getFacetsConfig();
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(indexSearcher.getIndexReader(), facetsConfig);

      // Build facets from filtered results
      Facets facets = new SortedSetDocValuesFacetCounts(state, facetsCollector);

      // Get all facet properties from registry
      CollectionRegistry registry = collectionRegistryService.getCurrentRegistry();
      Map<String, PropertyDescriptor> allProperties = registry.getGlobalPropertyRegistry();

      // Collect facet results for each facet dimension
      for (PropertyDescriptor property : allProperties.values()) {
        if (!property.isFacet()) {
          continue;
        }

        // Skip private facets for unauthorized users
        if (Boolean.TRUE.equals(property.getIsPrivate()) && !isUserAuthorized()) {
          continue;
        }

        String dimensionName = property.getName();
        int dimensionLimit =
            RELEASED_YEAR_FACET.equalsIgnoreCase(dimensionName) ? Integer.MAX_VALUE : limit;

        FacetResult facetResult = facets.getTopChildren(dimensionLimit, dimensionName);
        if (facetResult != null) {
          allResults.add(facetResult);
        }
      }

      // Add frequencies for selected low-frequency facets
      addLowFreqSelectedFacets(selectedFacetFreq, selectedFacets, facets);

    } catch (IOException ex) {
      log.error("IO error while getting facets for query: {}", drillDownQuery, ex);
    } catch (Exception ex) {
      log.error("Error while getting facets for query: {}", drillDownQuery, ex);
    } finally {
      if (indexSearcher != null) {
        try {
          indexManager.releaseSearcher(IndexName.SUBMISSION, indexSearcher);
        } catch (IOException ex) {
          log.error("Error releasing index searcher", ex);
        }
      }
    }

    return allResults;
  }

  /**
   * Retrieves a specific facet dimension with its values and counts.
   *
   * <p>This method is useful for dynamic facet loading or refreshing individual facets without
   * retrieving all facets. It applies the same filtering logic as getAllFacets but returns only the
   * requested dimension.
   *
   * @param collectionName the collection to search within
   * @param dimensionName the facet dimension to retrieve
   * @param baseQuery the already-built base query (should include field filters if needed)
   * @param selectedFacets map of currently selected facets
   * @return facet dimension data with children values and counts, or null if dimension is
   *     invalid/not found
   */
  public FacetDimensionDTO getDimension(
      String collectionName,
      String dimensionName,
      Query baseQuery,
      Map<String, List<String>> selectedFacets) {

    IndexSearcher indexSearcher = null;

    try {
      // Get FacetsConfig
      FacetsConfig facetsConfig = taxonomyManager.getFacetsConfig();

      // Apply security constraints
      Query securedQuery = securityQueryBuilder.applySecurity(baseQuery);

      // Apply facet filters
      DrillDownQuery drillDownQuery =
          addFacetDrillDownFilters(
              facetsConfig, securedQuery, convertToPropertyMap(selectedFacets));

      // Acquire main index searcher
      indexSearcher = indexManager.acquireSearcher(IndexName.SUBMISSION);

      // Create collector for facets
      FacetsCollector facetsCollector = new FacetsCollector();

      // Execute search with ALL filters applied
      indexSearcher.search(drillDownQuery, facetsCollector);

      // Build state for sorted set doc values facets
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(indexSearcher.getIndexReader(), facetsConfig);

      // Build facets from filtered results
      Facets facets = new SortedSetDocValuesFacetCounts(state, facetsCollector);

      // Get property descriptor for this dimension
      CollectionRegistry registry = collectionRegistryService.getCurrentRegistry();
      PropertyDescriptor property = registry.getPropertyDescriptor(dimensionName);

      if (property == null || !property.isFacet()) {
        log.warn("Dimension {} is not a valid facet", dimensionName);
        return null;
      }

      // Check if private and user unauthorized
      if (Boolean.TRUE.equals(property.getIsPrivate()) && !isUserAuthorized()) {
        log.debug("User not authorized to access private dimension: {}", dimensionName);
        return null;
      }

      // Get facet results for this dimension
      FacetResult facetResult = facets.getTopChildren(MAX_FACET_LIMIT, dimensionName);

      // Build frequencies for selected facets in this dimension
      Map<String, Integer> selectedFacetFreq = new HashMap<>();
      if (selectedFacets != null && selectedFacets.containsKey(dimensionName)) {
        for (String value : selectedFacets.get(dimensionName)) {
          try {
            Number frequency = facets.getSpecificValue(dimensionName, value);
            if (frequency != null) {
              selectedFacetFreq.put(value, frequency.intValue());
            }
          } catch (IOException ex) {
            log.debug("Could not get frequency for {}:{}", dimensionName, value, ex);
          }
        }
      }

      return buildFacetDimension(
          property,
          facetResult,
          selectedFacetFreq.isEmpty() ? null : selectedFacetFreq,
          Integer.MAX_VALUE);

    } catch (IOException ex) {
      log.error(
          "IO error while getting dimension {} for collection {}",
          dimensionName,
          collectionName,
          ex);
    } catch (Exception ex) {
      log.error(
          "Error while getting dimension {} for collection {}", dimensionName, collectionName, ex);
    } finally {
      if (indexSearcher != null) {
        try {
          indexManager.releaseSearcher(IndexName.SUBMISSION, indexSearcher);
        } catch (IOException ex) {
          log.error("Error releasing index searcher", ex);
        }
      }
    }

    return null;
  }

  /**
   * Retrieves all facets for a collection with optional query filtering.
   *
   * <p>This method filters facets based on collection-specific configuration and applies special
   * handling for certain facet types (e.g., release year sorting, collection visibility).
   *
   * @param collectionName the collection to search within (case-insensitive)
   * @param baseQuery the already-built base query (should include field filters if needed)
   * @param limit maximum number of values per facet (ignored for release_year which returns all)
   * @param selectedFacets map of currently selected facet dimensions to their values (for
   *     drill-down filtering)
   * @return list of facet dimensions with their values and counts, never null (may be empty)
   */
  public List<FacetDimensionDTO> getAllFacets(
      String collectionName, Query baseQuery, int limit, Map<String, List<String>> selectedFacets) {

    List<FacetDimensionDTO> result = new ArrayList<>();

    try {
      // 1. Apply security constraints
      Query securedQuery = securityQueryBuilder.applySecurity(baseQuery);

      // 2. Apply facet drill-down filters (separate from field filters)
      FacetsConfig facetsConfig = taxonomyManager.getFacetsConfig();
      DrillDownQuery drillDownQuery =
          addFacetDrillDownFilters(
              facetsConfig,
              securedQuery,
              convertToPropertyMap(
                  selectedFacets != null ? selectedFacets : Collections.emptyMap()));

      // 3. Get facet results with selected facet frequencies
      Map<String, Map<String, Integer>> selectedFacetFreq = new HashMap<>();
      List<FacetResult> facetResults =
          getFacetsForQuery(
              drillDownQuery,
              limit,
              selectedFacetFreq,
              selectedFacets != null ? selectedFacets : Collections.emptyMap());

      // 4. Get valid facets for this collection (public + collection-specific)
      CollectionRegistry registry = collectionRegistryService.getCurrentRegistry();
      List<PropertyDescriptor> validFacets =
          collectionRegistryService.getPublicAndCollectionRelatedProperties(collectionName);

      Set<String> validFacetNames =
          validFacets.stream()
              .filter(PropertyDescriptor::isFacet)
              .map(PropertyDescriptor::getName)
              .collect(Collectors.toSet());

      // 5. Convert facet results to DTOs
      for (FacetResult facetResult : facetResults) {
        if (facetResult == null || !validFacetNames.contains(facetResult.dim)) {
          continue;
        }

        PropertyDescriptor property = registry.getPropertyDescriptor(facetResult.dim);
        if (property == null) {
          continue;
        }

        // Skip collection facet for non-public collections without sub-collections
        if (shouldHideCollectionFacet(collectionName, property)) {
          continue;
        }

        FacetDimensionDTO dimension =
            buildFacetDimension(
                property, facetResult, selectedFacetFreq.get(facetResult.dim), limit);

        if (dimension != null) {
          result.add(dimension);
        }
      }

    } catch (Exception ex) {
      log.error("Error getting facets for collection {}", collectionName, ex);
    }

    return result;
  }

  /**
   * Checks if the collection facet should be hidden.
   *
   * <p>Collection facet is only shown for:
   *
   * <ul>
   *   <li>The public collection (always visible)
   *   <li>Collections that have sub-collections
   * </ul>
   *
   * @param collectionName the collection being queried
   * @param property the property descriptor to check
   * @return true if the facet should be hidden, false otherwise
   */
  private boolean shouldHideCollectionFacet(String collectionName, PropertyDescriptor property) {
    if (!Constants.PUBLIC.equalsIgnoreCase(collectionName)
        && "facet.collection".equalsIgnoreCase(property.getName())) {
      // TODO: Implement sub-collection check when IndexManager is available
      // return !indexManager.getSubCollectionMap().containsKey(collectionName.toLowerCase());
      return false; // For now, show collection facet
    }
    return false;
  }

  /**
   * Adds frequencies for selected facets that may have low frequency.
   *
   * <p>Selected facets with low hit counts may not appear in the top N results. This method
   * explicitly retrieves their frequencies so they can be displayed in the UI to show the user what
   * filters are currently active.
   *
   * @param selectedFacetFreq map to populate with frequencies
   * @param selectedFacets map of selected facet dimensions to values
   * @param facets the facets object to query for frequencies
   */
  private void addLowFreqSelectedFacets(
      Map<String, Map<String, Integer>> selectedFacetFreq,
      Map<String, List<String>> selectedFacets,
      Facets facets) {

    if (facets == null || selectedFacets == null) {
      return;
    }

    for (Map.Entry<String, List<String>> entry : selectedFacets.entrySet()) {
      String dimension = entry.getKey();
      List<String> values = entry.getValue();

      if (dimension == null || values == null || values.isEmpty()) {
        continue;
      }

      Map<String, Integer> freqMap =
          selectedFacetFreq.computeIfAbsent(dimension, k -> new HashMap<>());

      for (String value : values) {
        if (value == null || value.isEmpty()) {
          continue;
        }

        try {
          Number frequency = facets.getSpecificValue(dimension, value);
          if (frequency != null) {
            freqMap.put(value, frequency.intValue());
          }
        } catch (IOException ex) {
          log.debug("Could not get frequency for {}:{}", dimension, value, ex);
        }
      }
    }
  }

  /**
   * Converts a map of dimension names to values into a map of PropertyDescriptors to values.
   *
   * <p>This conversion ensures that only valid, configured facet dimensions are processed and
   * allows access to property metadata (e.g., toLowerCase settings).
   *
   * @param facetsByName map of dimension names to selected values
   * @return map of PropertyDescriptors to selected values, empty if input is null/empty
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
   * Builds a FacetDimensionDTO from a property descriptor and facet result.
   *
   * <p>Handles special cases:
   *
   * <ul>
   *   <li>Invisible NA values (filtered out based on property configuration)
   *   <li>Release year sorting (reverse chronological, NA removed if at top, limit applied)
   *   <li>Selected facets (included even if they have low counts)
   * </ul>
   *
   * @param property the property descriptor containing facet metadata
   * @param facetResult the facet result with counts from Lucene
   * @param selectedFacetFreq frequencies of selected facets (may be null)
   * @param limit maximum number of children to include (applied after sorting for release year)
   * @return FacetDimensionDTO containing dimension metadata and values, never null
   */
  private FacetDimensionDTO buildFacetDimension(
      PropertyDescriptor property,
      FacetResult facetResult,
      Map<String, Integer> selectedFacetFreq,
      int limit) {

    FacetDimensionDTO dimension = new FacetDimensionDTO();
    dimension.setName(property.getName());
    dimension.setTitle(property.getTitle());
    dimension.setType(property.getFacetType());

    List<FacetValueDTO> children = new ArrayList<>();

    // Add selected facets first (they may have low counts and not appear in top results)
    if (selectedFacetFreq != null) {
      for (Map.Entry<String, Integer> entry : selectedFacetFreq.entrySet()) {
        FacetValueDTO selectedValue = new FacetValueDTO();
        selectedValue.setName(entry.getKey());
        selectedValue.setValue(entry.getKey());
        selectedValue.setHits(entry.getValue());
        children.add(selectedValue);
      }
    }

    // Determine NA visibility
    boolean invisibleNA = Boolean.FALSE.equals(property.getNaVisible());
    String naDefaultStr = property.getDefaultValue() != null ? property.getDefaultValue() : "N/A";

    // Add facet values from results
    if (facetResult != null && facetResult.labelValues != null) {
      for (LabelAndValue labelAndValue : facetResult.labelValues) {

        // Skip invisible NA values
        if (invisibleNA && labelAndValue.label.equalsIgnoreCase(naDefaultStr)) {
          continue;
        }

        // Skip if already added as selected facet
        if (selectedFacetFreq != null && selectedFacetFreq.containsKey(labelAndValue.label)) {
          continue;
        }

        FacetValueDTO facetValue = new FacetValueDTO();
        facetValue.setName(labelAndValue.label);
        facetValue.setValue(labelAndValue.label);
        facetValue.setHits(labelAndValue.value.intValue());
        children.add(facetValue);
      }
    }

    // Sort by name (alphabetical)
    children.sort(Comparator.comparing(FacetValueDTO::getName));

    // Special handling for release year facet
    if (RELEASED_YEAR_FACET.equalsIgnoreCase(property.getName())) {
      // Reverse sort (newest first)
      Collections.reverse(children);

      // Remove N/A if it's at the top
      if (!children.isEmpty() && "N/A".equalsIgnoreCase(children.get(0).getName())) {
        children.remove(0);
      }

      // Apply limit for release year
      if (children.size() > limit) {
        children = children.stream().limit(limit).collect(Collectors.toList());
      }
    }

    dimension.setChildren(children);
    return dimension;
  }

  /**
   * Checks if the current user is authorized to see private facets.
   *
   * <p>Private facets are visible only to authenticated users (not anonymous).
   *
   * @return true if user is authenticated, false otherwise
   */
  private boolean isUserAuthorized() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && !"anonymousUser".equals(authentication.getPrincipal());
  }
}
