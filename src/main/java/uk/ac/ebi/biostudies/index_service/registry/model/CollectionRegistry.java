package uk.ac.ebi.biostudies.index_service.registry.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * The {@code uk.ac.ebi.biostudies.index.service.CollectionRegistry} class acts as a container and
 * registry for multiple {@link CollectionDescriptor} instances. It provides a way to manage,
 * access, and manipulate a collection of collection schemas or descriptors in a centralized manner.
 *
 * <p>This class supports retrieving and searching for specific collection descriptors by collection
 * name. It is useful in systems where multiple collections are defined dynamically and need to be
 * handled collectively, such as in schema-driven data management or configuration systems.
 *
 * <p>Example usage:
 *
 * <pre>
 *     List&lt;CollectionDescriptor&gt; descriptors = ...;
 *     CollectionRegistry registry = new CollectionRegistry(descriptors);
 *     CollectionDescriptor descriptor = registry.getCollectionDescriptor("idr");
 * </pre>
 */
@Getter
@ToString
@EqualsAndHashCode
public class CollectionRegistry {

  private final List<CollectionDescriptor> collections;
  private final Map<String, CollectionDescriptor> registry;

  /**
   * A map where the key is the name of a property/field, and the value is the descriptor containing
   * information about how to index that property. This registry aggregates all properties across
   * all collections, so it contains every property defined in any collection.
   */
  private final Map<String, PropertyDescriptor> globalPropertyRegistry;

  /**
   * Constructs a CollectionRegistry from a list of descriptors. Defensive copy and immutable
   * collections are used internally.
   *
   * @param collections the list of collections (may be null or empty)
   */
  public CollectionRegistry(List<CollectionDescriptor> collections) {
    this.collections = collections == null ? Collections.emptyList() : List.copyOf(collections);

    Map<String, CollectionDescriptor> map = new HashMap<>();
    for (CollectionDescriptor collection : this.collections) {
      if (collection != null && collection.getCollectionName() != null) {
        map.put(collection.getCollectionName(), collection);
      }
    }
    this.registry = Collections.unmodifiableMap(map);
    this.globalPropertyRegistry = createGlobalPropertyRegistry();
  }

  private Map<String, PropertyDescriptor> createGlobalPropertyRegistry() {
    Map<String, PropertyDescriptor> tempMap = new HashMap<>();
    for (CollectionDescriptor cd : collections) {
      for (Map.Entry<String, PropertyDescriptor> entry : cd.getPropertyRegistry().entrySet()) {
        String key = entry.getKey();
        if (tempMap.containsKey(key)) {
          throw new IllegalStateException("Duplicate property name across collections: " + key);
        }
        tempMap.put(key, entry.getValue());
      }
    }
    return Collections.unmodifiableMap(tempMap);
  }

  /**
   * Retrieves a {@link CollectionDescriptor} by its collection name. Returns {@code null} if no
   * matching collection is found.
   *
   * @param collectionName name of the collection to retrieve (may be null)
   * @return a matching CollectionDescriptor, or null if none found
   */
  public CollectionDescriptor getCollectionDescriptor(String collectionName) {
    if (collectionName == null) {
      return null;
    }
    return registry.get(collectionName);
  }

  /**
   * Checks whether the registry contains a collection with the specified name.
   *
   * @param collectionName the collection name to check (may be null)
   * @return true if a collection with the given name exists, false otherwise
   */
  public boolean containsCollection(String collectionName) {
    return collectionName != null && registry.containsKey(collectionName);
  }

  /**
   * Retrieves the PropertyDescriptor associated with the given field name from the global property
   * registry aggregated across all contained collections.
   *
   * @param fieldName the unique name of the property/field to look up
   * @return the PropertyDescriptor corresponding to the field name, or {@code null} if no such
   *     property exists in the registry
   */
  public PropertyDescriptor getPropertyDescriptor(String fieldName) {
    return globalPropertyRegistry.get(fieldName);
  }
}
