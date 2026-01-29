package uk.ac.ebi.biostudies.index_service.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for collection hierarchy and subcollection relationships.
 *
 * <p>Defines parent-child relationships between collections. When a user searches or filters by a
 * parent collection, results automatically include documents from all child collections.
 *
 * <p>Configuration is loaded from subcollections.yml in the classpath.
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties()
public class SubCollectionConfig {

  /** Map of parent collection names to their configuration. */
  private Map<String, CollectionHierarchy> subcollections = new HashMap<>();

  /**
   * Cached map of parent → list of children for efficient lookup. Built during initialization from
   * the subcollections configuration.
   */
  @Getter private Map<String, List<String>> parentToChildrenMap;

  /** Cached map of child → parent for reverse lookup. */
  @Getter private Map<String, String> childToParentMap;

  @PostConstruct
  public void init() {
    buildCaches();
    logHierarchy();
  }

  /** Builds efficient lookup caches from the configuration. */
  private void buildCaches() {
    parentToChildrenMap = new HashMap<>();
    childToParentMap = new HashMap<>();

    subcollections.forEach(
        (parent, hierarchy) -> {
          List<String> children = hierarchy.getChildren();
          if (children != null && !children.isEmpty()) {
            // Store parent → children mapping
            parentToChildrenMap.put(parent, List.copyOf(children));

            // Build child → parent reverse mapping
            children.forEach(
                child -> {
                  if (childToParentMap.containsKey(child)) {
                    log.warn(
                        "Collection '{}' has multiple parents: '{}' and '{}'",
                        child,
                        childToParentMap.get(child),
                        parent);
                  }
                  childToParentMap.put(child, parent);
                });
          }
        });

    // Make immutable
    parentToChildrenMap = Map.copyOf(parentToChildrenMap);
    childToParentMap = Map.copyOf(childToParentMap);
  }

  /** Logs the configured hierarchy for debugging. */
  private void logHierarchy() {
    if (subcollections.isEmpty()) {
      log.info("No subcollection hierarchies configured");
      return;
    }

    log.info("Loaded collection hierarchy:");
    subcollections.forEach(
        (parent, hierarchy) -> {
          List<String> children = hierarchy.getChildren();
          if (children != null && !children.isEmpty()) {
            log.info("  {} → {}", parent, children);
          }
        });
  }

  /**
   * Gets all children for a parent collection, or empty list if none.
   *
   * @param parent the parent collection name
   * @return list of child collection names
   */
  public List<String> getChildren(String parent) {
    if (parentToChildrenMap == null || parent == null) {
      return Collections.emptyList();
    }
    return parentToChildrenMap.getOrDefault(parent, List.of());
  }

  /**
   * Gets the parent of a collection, or null if it has no parent.
   *
   * @param child the child collection name
   * @return parent collection name, or null
   */
  public String getParent(String child) {
    if (childToParentMap == null || child == null) {
      return null;
    }
    return childToParentMap.get(child);
  }

  /**
   * Checks if a collection has children.
   *
   * @param collectionName the collection name
   * @return true if it has children
   */
  public boolean hasChildren(String collectionName) {
    return parentToChildrenMap.containsKey(collectionName);
  }

  /**
   * Gets all collections in a hierarchy (parent + all children).
   *
   * @param collectionName the parent or child collection name
   * @return list containing the collection and all related collections
   */
  public List<String> getHierarchy(String collectionName) {
    List<String> hierarchy = new ArrayList<>();

    // Check if it's a parent collection
    if (parentToChildrenMap.containsKey(collectionName)) {
      // Add the parent itself
      hierarchy.add(collectionName);
      // Add all its children
      hierarchy.addAll(parentToChildrenMap.get(collectionName));
    }
    // Check if it's a child collection
    else if (childToParentMap.containsKey(collectionName)) {
      String parent = childToParentMap.get(collectionName);
      // Add the parent
      hierarchy.add(parent);
      // Add all children (including the one we queried for)
      hierarchy.addAll(parentToChildrenMap.get(parent));
    }
    // Otherwise, standalone collection
    else {
      hierarchy.add(collectionName);
    }

    return hierarchy;
  }

  /** Configuration for a single parent collection and its children. */
  @Data
  public static class CollectionHierarchy {
    private List<String> children = new ArrayList<>();
    private String description;
  }
}
