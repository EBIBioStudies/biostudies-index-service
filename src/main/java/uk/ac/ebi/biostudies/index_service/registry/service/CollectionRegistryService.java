package uk.ac.ebi.biostudies.index_service.registry.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.registry.loader.CollectionRegistryLoader;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

@Slf4j
@Service
@Getter
public class CollectionRegistryService {

  private final CollectionRegistryLoader loader;
  private final CollectionRegistryValidator validator;
  private volatile CollectionRegistry currentRegistry; // volatile for safe publish

  private Map<String, List<PropertyDescriptor>> collectionPropertiesCache;
  private List<PropertyDescriptor> publicProperties;

  @Value("${collection.registry.location}")
  private String collectionRegistryLocation;

  public CollectionRegistryService(
      CollectionRegistryLoader loader, CollectionRegistryValidator validator) {
    this.loader = loader;
    this.validator = validator;
  }

  private void initCache() {
    if (currentRegistry == null) {
      throw new IllegalStateException("Cannot init cache: registry not loaded");
    }
    CollectionRegistry registry = currentRegistry;

    // Single lookup for public
    List<PropertyDescriptor> publicProps = List.of();
    CollectionDescriptor publicDesc = registry.getCollectionDescriptor(Constants.PUBLIC);
    if (publicDesc != null) {
      publicProps = List.copyOf(publicDesc.getProperties());
    }
    publicProperties = publicProps;

    // Cache with LOWERCASE KEYS
    Map<String, List<PropertyDescriptor>> cache = new HashMap<>();
    for (CollectionDescriptor cd : registry.getCollections()) {
      String lowerKey = cd.getCollectionName().toLowerCase();
      cache.put(lowerKey, List.copyOf(cd.getProperties()));
    }
    collectionPropertiesCache = Map.copyOf(cache);

    log.debug(
        "Cache initialized: {} collections, {} public properties",
        collectionPropertiesCache.size(),
        publicProperties.size());
  }

  /**
   * Loads the collection registry from the specified resource location. Caches the result
   * internally for future use. Initializes property caches for fast lookups.
   *
   * @return the loaded {@link CollectionRegistry}
   * @throws IllegalStateException if resource cannot be loaded
   */
  public synchronized CollectionRegistry loadRegistry() {
    log.debug("Loading collection registry from {}", collectionRegistryLocation);
    CollectionRegistry registry = loader.loadFromResource(collectionRegistryLocation);
    validator.validate(registry);
    this.currentRegistry = registry;
    initCache();
    log.debug("{} properties successfully loaded", registry.getGlobalPropertyRegistry().size());
    return registry;
  }

  /**
   * Returns all {@link PropertyDescriptor}s applicable to the given collection, including public
   * properties that apply universally.
   *
   * <p>Behaviour:
   *
   * <ul>
   *   <li>If {@code collectionName} is {@code null}, {@link Constants#PUBLIC}, or missing, returns
   *       only public properties.
   *   <li>Otherwise, returns collection-specific properties plus public properties.
   * </ul>
   *
   * @param collectionName collection logical name (case-sensitive), or {@code null}
   * @return unmodifiable list of property descriptors; empty if registry not loaded
   */
  public List<PropertyDescriptor> getPublicAndCollectionRelatedProperties(String collectionName) {
    if (publicProperties == null) {
      return List.of();
    }
    List<PropertyDescriptor> result = new ArrayList<>(publicProperties);
    if (collectionName != null) {
      String lowerName = collectionName.toLowerCase();
      if (!Constants.PUBLIC.equalsIgnoreCase(lowerName)) {  // Case-insensitive public check
        List<PropertyDescriptor> specific = collectionPropertiesCache != null
            ? collectionPropertiesCache.getOrDefault(lowerName, List.of())
            : List.of();
        result.addAll(specific);
      }
    }
    return List.copyOf(result);
  }
}
