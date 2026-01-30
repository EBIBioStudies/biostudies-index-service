package uk.ac.ebi.biostudies.index_service.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.LabelAndValue;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;
import uk.ac.ebi.biostudies.index_service.search.query.FacetService;

@Component
public class FacetFormatter {

  private final CollectionRegistryService collectionRegistryService;
  private final FacetService facetService;

  public FacetFormatter(
      CollectionRegistryService collectionRegistryService, FacetService facetService) {
    this.collectionRegistryService = collectionRegistryService;
    this.facetService = facetService;
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
  public List<FacetDimensionDTO> getFacetsForUI(
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
    if (!FieldName.COLLECTION.getName().equalsIgnoreCase(dimensionName)) {
      return false;
    }

    // Show collection facet for public collection
    if (Constants.PUBLIC.equalsIgnoreCase(collectionName)) {
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
}
