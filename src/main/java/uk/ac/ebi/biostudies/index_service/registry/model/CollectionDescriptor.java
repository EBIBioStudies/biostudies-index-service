package uk.ac.ebi.biostudies.index_service.registry.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import uk.ac.ebi.biostudies.index_service.registry.dto.PropertyDescriptorDto;
import uk.ac.ebi.biostudies.index_service.registry.mapper.PropertyDescriptorConverter;

/**
 * Describes a single named collection and the set of indexed properties that belong to it. Each
 * {@code CollectionDescriptor} contains a collection name and a list of {@link PropertyDescriptor}
 * instances describing each field.
 *
 * <p>This class builds an internal registry that maps property {@code name} to its {@link
 * PropertyDescriptor} for fast lookups performed by indexing, validation and mapping code. The
 * registry is populated at construction time and remains immutable afterwards.
 *
 * <p>Thread-safety: instances of this class are effectively immutable after construction (fields
 * are final and the internal collections are unmodifiable), so they may be safely shared across
 * threads.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * CollectionDescriptor desc = new CollectionDescriptor("idr", properties);
 * PropertyDescriptor p = desc.getPropertyByName("facet.idr.study_type");
 * }</pre>
 */
@Getter
public class CollectionDescriptor {

  /** Logical name of the collection (e.g. "idr", "arrayexpress"). Never null. */
  private final String collectionName;

  /**
   * Unmodifiable list of property descriptors belonging to this collection. Never null (may be
   * empty). -- GETTER -- Return an unmodifiable view of the property descriptors for this
   * collection.
   */
  private final List<PropertyDescriptor> properties;

  /**
   * Internal registry mapping property name to descriptor for fast lookup. Never null (may be
   * empty).
   */
  private final Map<String, PropertyDescriptor> propertyRegistry;

  /**
   * Construct a {@code CollectionDescriptor}.
   *
   * @param collectionName logical collection name (must not be null or blank)
   * @param properties list of property descriptors (may be null or empty)
   * @throws NullPointerException if collectionName is null
   * @throws IllegalArgumentException if collectionName is blank
   */
  public CollectionDescriptor(String collectionName, List<PropertyDescriptorDto> properties) {
    this.collectionName = Objects.requireNonNull(collectionName);
    this.properties =
        properties == null
            ? Collections.emptyList()
            : properties.stream().map(PropertyDescriptorConverter::fromDto).toList();

    Map<String, PropertyDescriptor> map = new HashMap<>();
    for (PropertyDescriptor pd : this.properties) {
      map.put(pd.getName(), pd);
    }
    this.propertyRegistry = Collections.unmodifiableMap(map);
  }

  /**
   * Lookup a property descriptor by its programmatic name.
   *
   * @param name the property name to look up (may be null)
   * @return the {@link PropertyDescriptor} or {@code null} if no such property exists
   */
  public PropertyDescriptor getPropertyByName(String name) {
    if (name == null) {
      return null;
    }
    return propertyRegistry.get(name);
  }

  /**
   * Return {@code true} when this collection contains a property with the given name.
   *
   * @param name property name to check (may be null)
   * @return true if property exists
   */
  public boolean containsProperty(String name) {
    return name != null && propertyRegistry.containsKey(name);
  }

  /**
   * Returns the set of property names for this collection.
   *
   * @return an unmodifiable set of property names (never null)
   */
  public Set<String> getPropertyNames() {
    return propertyRegistry.keySet();
  }

  @Override
  public String toString() {
    return "CollectionDescriptor{"
        + "collectionName='"
        + collectionName
        + '\''
        + ", propertiesCount="
        + properties.size()
        + '}';
  }
}
