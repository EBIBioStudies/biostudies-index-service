package uk.ac.ebi.biostudies.index_service.search.query;

import java.io.IOException;
import java.util.*;
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
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;

/**
 * Service for applying faceted search filtering to Lucene queries.
 *
 * <p>This service creates drill-down queries that allow users to refine search results by selecting
 * specific facet values (dimensions). It integrates with Apache Lucene's faceted search
 * capabilities to provide hierarchical filtering on indexed collections.
 *
 * <p>Facet information is stored directly in the main index (no separate taxonomy index).
 */
@Slf4j
@Service
public class FacetService {

  /** Name of the release year facet dimension */
  public static final String RELEASED_YEAR_FACET = "release_year";

  private final IndexManager indexManager;
  private final TaxonomyManager taxonomyManager;
  private final CollectionRegistryService collectionRegistryService;

  public FacetService(
      IndexManager indexManager,
      TaxonomyManager taxonomyManager,
      CollectionRegistryService collectionRegistryService) {
    this.indexManager = indexManager;
    this.taxonomyManager = taxonomyManager;
    this.collectionRegistryService = collectionRegistryService;
  }

  /**
   * Constructs a drill-down query by applying facet filters to a base query.
   *
   * @param facetsConfig the Lucene facets configuration
   * @param primaryQuery the base search query
   * @param selectedFacetValues map of property descriptors to selected values
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
   * <p>Executes the query with all filters applied, so facet counts reflect
   * the current filter selection (dependent facet counts).
   *
   * @param drillDownQuery the drill-down query with applied facet filters
   * @param limit maximum number of top children to retrieve per dimension
   * @param selectedFacetFreq map to populate with frequencies of selected facets
   * @param selectedFacets map of currently selected facet dimensions to their values
   * @return list of facet results for all configured dimensions
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
          new DefaultSortedSetDocValuesReaderState(
              indexSearcher.getIndexReader(),
              facetsConfig
          );

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
   * @param collectionName the collection to search within
   * @param dimensionName the facet dimension to retrieve
   * @param query the base query
   * @param selectedFacets map of currently selected facets
   * @return facet dimension data with children values and counts
   */
  public FacetDimension getDimension(
      String collectionName,
      String dimensionName,
      Query query,
      Map<String, List<String>> selectedFacets) {

    IndexSearcher indexSearcher = null;

    try {
      // Get FacetsConfig
      FacetsConfig facetsConfig = taxonomyManager.getFacetsConfig();

      // Apply facet filters
      DrillDownQuery drillDownQuery =
          addFacetDrillDownFilters(facetsConfig, query, convertToPropertyMap(selectedFacets));

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
      FacetResult facetResult = facets.getTopChildren(Integer.MAX_VALUE, dimensionName);

      return buildFacetDimension(property, facetResult);

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
   * Adds frequencies for selected facets that may have low frequency.
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
   * @param facetsByName map of dimension names to selected values
   * @return map of PropertyDescriptors to selected values
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
   * Builds a FacetDimension object from a property descriptor and facet result.
   *
   * @param property the property descriptor
   * @param facetResult the facet result with counts
   * @return FacetDimension containing dimension metadata and values
   */
  private FacetDimension buildFacetDimension(PropertyDescriptor property, FacetResult facetResult) {

    List<FacetValue> values = new ArrayList<>();

    if (facetResult != null && facetResult.labelValues != null) {
      for (LabelAndValue labelAndValue : facetResult.labelValues) {
        values.add(new FacetValue(labelAndValue.label, labelAndValue.value.intValue()));
      }

      // Sort by label
      values.sort(Comparator.comparing(FacetValue::getLabel));
    }

    return new FacetDimension(property.getName(), property.getTitle(), values);
  }

  /**
   * Checks if the current user is authorized to see private facets. Private facets are visible only
   * to authenticated users.
   *
   * @return true if user is authenticated, false otherwise
   */
  private boolean isUserAuthorized() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && !"anonymousUser".equals(authentication.getPrincipal());
  }

  // Inner classes remain the same...
  public static class FacetDimension {
    private final String name;
    private final String title;
    private final List<FacetValue> values;

    public FacetDimension(String name, String title, List<FacetValue> values) {
      this.name = name;
      this.title = title;
      this.values = values;
    }

    public String getName() {
      return name;
    }

    public String getTitle() {
      return title;
    }

    public List<FacetValue> getValues() {
      return values;
    }
  }

  public static class FacetValue {
    private final String label;
    private final int count;

    public FacetValue(String label, int count) {
      this.label = label;
      this.count = count;
    }

    public String getLabel() {
      return label;
    }

    public int getCount() {
      return count;
    }
  }
}
