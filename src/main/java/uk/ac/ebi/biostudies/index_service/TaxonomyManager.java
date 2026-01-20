package uk.ac.ebi.biostudies.index_service;

import lombok.Getter;
import org.apache.lucene.facet.FacetsConfig;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

@Getter
@Component
public class TaxonomyManager {
  private FacetsConfig facetsConfig;

  public void init(CollectionRegistry collectionRegistry) {
    facetsConfig = new FacetsConfig();
    var allProps = collectionRegistry.getGlobalPropertyRegistry();
    allProps.forEach(
        (name, property) -> {
          if (property.isFacet()) {
            if (property.getMultiValued() == null || Boolean.TRUE.equals(property.getMultiValued())) {
              facetsConfig.setMultiValued(name, true);
            }
          }
        });
  }
}
