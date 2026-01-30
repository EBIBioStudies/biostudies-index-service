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

/**
 * Service for loading, validating, and providing access to the collection registry configuration.
 *
 * <p>This service manages the lifecycle of the {@link CollectionRegistry}, which defines how
 * different fields in BioStudies collections should be indexed. It provides thread-safe access to
 * registry data and maintains performance-optimized caches for frequently accessed information.
 *
 * <p><strong>Thread Safety:</strong> This service is initialized once at startup via {@link
 * #loadRegistry()}. After initialization, all fields are effectively immutable and all read
 * operations are lock-free and thread-safe.
 *
 * <p><strong>Performance:</strong> After initialization, all read operations are lock-free and use
 * cached immutable collections for optimal performance under high concurrency.
 *
 * @see CollectionRegistry
 * @see PropertyDescriptor
 * @see CollectionDescriptor
 */
@Slf4j
@Service
public class CollectionRegistryService {

  @Getter private final CollectionRegistryLoader loader;
  @Getter private final CollectionRegistryValidator validator;

  /**
   * The current registry instance. Written once during initialization, then treated as immutable.
   * Using a holder object ensures proper happens-before relationship without volatile overhead.
   */
  private final RegistryHolder registryHolder = new RegistryHolder();

  @Value("${collection.registry.location}")
  @Getter
  private String collectionRegistryLocation;

  /**
   * Constructs a new CollectionRegistryService.
   *
   * @param loader the loader responsible for reading registry configuration from resources
   * @param validator the validator for ensuring registry consistency and correctness
   */
  public CollectionRegistryService(
      CollectionRegistryLoader loader, CollectionRegistryValidator validator) {
    this.loader = loader;
    this.validator = validator;
  }

  /**
   * Loads the collection registry from the configured resource location.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Loads the registry configuration from {@link #collectionRegistryLocation}
   *   <li>Validates the registry for consistency
   *   <li>Stores the registry for future access
   *   <li>Initializes performance caches
   * </ol>
   *
   * <p><strong>Thread Safety:</strong> This method is synchronized to prevent concurrent
   * initialization. Subsequent read operations are lock-free.
   *
   * <p><strong>Typical Usage:</strong> Call once at application startup, either explicitly or via
   * dependency injection initialization.
   *
   * @return the loaded and validated {@link CollectionRegistry}
   * @throws IllegalStateException if the resource cannot be loaded or validation fails
   */
  public synchronized CollectionRegistry loadRegistry() {
    log.debug("Loading collection registry from {}", collectionRegistryLocation);
    CollectionRegistry registry = loader.loadFromResource(collectionRegistryLocation);
    validator.validate(registry);

    // Build all caches
    List<PropertyDescriptor> publicProps = buildPublicPropertiesCache(registry);
    Map<String, List<PropertyDescriptor>> collectionCache =
        buildCollectionPropertiesCache(registry);

    // Atomically publish all initialized data
    registryHolder.initialize(registry, publicProps, collectionCache);

    log.debug("{} properties successfully loaded", registry.getGlobalPropertyRegistry().size());
    log.debug(
        "Cache initialized: {} collections, {} public properties",
        collectionCache.size(),
        publicProps.size());

    return registry;
  }

  /**
   * Builds the public properties cache.
   *
   * @param registry the loaded registry
   * @return immutable list of public properties
   */
  private List<PropertyDescriptor> buildPublicPropertiesCache(CollectionRegistry registry) {
    CollectionDescriptor publicDesc = registry.getCollectionDescriptor(Constants.PUBLIC);
    if (publicDesc != null) {
      return List.copyOf(publicDesc.getProperties());
    }
    return List.of();
  }

  /**
   * Builds the collection properties cache with lowercase keys.
   *
   * @param registry the loaded registry
   * @return immutable map of collection name to properties
   */
  private Map<String, List<PropertyDescriptor>> buildCollectionPropertiesCache(
      CollectionRegistry registry) {
    Map<String, List<PropertyDescriptor>> cache = new HashMap<>();
    for (CollectionDescriptor cd : registry.getCollections()) {
      String lowerKey = cd.getCollectionName().toLowerCase();
      cache.put(lowerKey, List.copyOf(cd.getProperties()));
    }
    return Map.copyOf(cache);
  }

  /**
   * Returns the currently loaded collection registry.
   *
   * <p><strong>Performance:</strong> This method is lock-free and optimized for high-frequency
   * access.
   *
   * <p><strong>Thread Safety:</strong> Safe to call concurrently from multiple threads without
   * synchronization overhead.
   *
   * @return the current {@link CollectionRegistry} instance
   * @throws IllegalStateException if the registry has not been loaded yet via {@link
   *     #loadRegistry()}
   */
  public CollectionRegistry getCurrentRegistry() {
    return registryHolder.getRegistry();
  }

  /**
   * Returns all property descriptors applicable to the specified collection, including public
   * properties that apply universally.
   *
   * <p><strong>Behavior:</strong>
   *
   * <ul>
   *   <li>If {@code collectionName} is {@code null}, {@link Constants#PUBLIC}, or not found:
   *       returns only public properties
   *   <li>Otherwise: returns collection-specific properties plus public properties
   * </ul>
   *
   * <p><strong>Case Sensitivity:</strong> Collection name lookup is case-insensitive.
   *
   * <p><strong>Thread Safety:</strong> Lock-free reads from immutable cached collections.
   *
   * <p><strong>Example usage:</strong>
   *
   * <pre>{@code
   * List<PropertyDescriptor> arrayExpressProps =
   *     registryService.getPublicAndCollectionRelatedProperties("arrayexpress");
   * // Returns public properties + arrayexpress-specific properties
   * }</pre>
   *
   * @param collectionName the logical collection name (case-insensitive), or {@code null}
   * @return an unmodifiable list of property descriptors; empty list if registry not loaded
   */
  public List<PropertyDescriptor> getPublicAndCollectionRelatedProperties(String collectionName) {
    List<PropertyDescriptor> pubProps = registryHolder.getPublicProperties();
    if (pubProps.isEmpty()) {
      return List.of();
    }

    List<PropertyDescriptor> result = new ArrayList<>(pubProps);
    if (collectionName != null) {
      String lowerName = collectionName.toLowerCase();
      if (!Constants.PUBLIC.equalsIgnoreCase(lowerName)) {
        Map<String, List<PropertyDescriptor>> cache = registryHolder.getCollectionPropertiesCache();
        List<PropertyDescriptor> specific = cache.getOrDefault(lowerName, List.of());
        result.addAll(specific);
      }
    }
    return List.copyOf(result);
  }

  /**
   * Retrieves a property descriptor by its unique name from the global registry.
   *
   * <p>This method performs a lookup across all collections to find a property with the specified
   * name. The global property registry ensures that property names are unique system-wide.
   *
   * <p><strong>Thread Safety:</strong> Lock-free reads from immutable registry map.
   *
   * <p><strong>Performance:</strong> O(1) hash map lookup.
   *
   * <p><strong>Example usage:</strong>
   *
   * <pre>{@code
   * PropertyDescriptor organismProp = registryService.getPropertyDescriptor("facet.organism");
   * if (organismProp != null && organismProp.isFacet()) {
   *     // Use the property for faceted search indexing
   * }
   * }</pre>
   *
   * @param propertyName the unique name of the property to retrieve (e.g., "facet.organism",
   *     "field.title"). May be null.
   * @return the {@link PropertyDescriptor} matching the name, or {@code null} if not found or if
   *     propertyName is null
   * @throws IllegalStateException if the registry has not been loaded yet
   * @see CollectionRegistry#getPropertyDescriptor(String)
   * @see PropertyDescriptor
   */
  public PropertyDescriptor getPropertyDescriptor(String propertyName) {
    CollectionRegistry registry = registryHolder.getRegistry();
    return registry.getPropertyDescriptor(propertyName);
  }

  /**
   * Thread-safe holder for registry and its caches. All fields are written once during
   * initialization and then treated as immutable. The synchronized initialize method provides the
   * happens-before guarantee for safe publication.
   */
  private static class RegistryHolder {
    private CollectionRegistry registry;
    private List<PropertyDescriptor> publicProperties = List.of();
    private Map<String, List<PropertyDescriptor>> collectionPropertiesCache = Map.of();

    /**
     * Initializes all registry data atomically. Called once during service initialization.
     *
     * @param registry the loaded registry
     * @param publicProps immutable list of public properties
     * @param collectionCache immutable map of collection properties
     */
    synchronized void initialize(
        CollectionRegistry registry,
        List<PropertyDescriptor> publicProps,
        Map<String, List<PropertyDescriptor>> collectionCache) {
      this.registry = registry;
      this.publicProperties = publicProps;
      this.collectionPropertiesCache = collectionCache;
    }

    /**
     * Returns the registry. Thread-safe after initialization due to happens-before relationship
     * established by synchronized initialize().
     */
    CollectionRegistry getRegistry() {
      CollectionRegistry reg = this.registry;
      if (reg == null) {
        throw new IllegalStateException("Registry not initialized - call loadRegistry() first");
      }
      return reg;
    }

    /** Returns public properties cache. */
    List<PropertyDescriptor> getPublicProperties() {
      return this.publicProperties;
    }

    /** Returns collection properties cache. */
    Map<String, List<PropertyDescriptor>> getCollectionPropertiesCache() {
      return this.collectionPropertiesCache;
    }
  }
}
