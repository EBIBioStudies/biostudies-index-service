package uk.ac.ebi.biostudies.index_service;

import lombok.Getter;
import org.apache.lucene.facet.FacetsConfig;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

/**
 * Manages Lucene facet configuration for both collection-specific facets and EFO facets.
 *
 * <p>All facets use SortedSetDocValuesFacetField for consistency. EFO hierarchy is encoded as
 * depth-prefixed values (e.g., "0/phagocyte", "1/osteoclast").
 */
@Getter
@Component
public class TaxonomyManager {

  private FacetsConfig facetsConfig;

  /**
   * Initializes facets configuration from collection registry and adds EFO facet settings.
   *
   * @param collectionRegistry the collection registry with facet property descriptors
   */
  public void init(CollectionRegistry collectionRegistry) {
    facetsConfig = new FacetsConfig();

    // Configure collection-specific facets from registry
    var allProps = collectionRegistry.getGlobalPropertyRegistry();
    allProps.forEach(
        (name, property) -> {
          if (property.isFacet()) {
            if (property.getMultiValued() == null
                || Boolean.TRUE.equals(property.getMultiValued())) {
              facetsConfig.setMultiValued(name, true);
            }
          }
        });

    // Configure EFO facet (depth-encoded hierarchy)
    facetsConfig.setMultiValued("efo", true);
  }
}
