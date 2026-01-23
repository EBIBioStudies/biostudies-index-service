package uk.ac.ebi.biostudies.index_service.index.efo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/** Immutable in-memory model of EFO graph. Built via {@link Builder} during OWL parsing. */
@Getter
public class EFOModel {

  private final Map<String, EFONode> nodes;
  private final Map<String, Set<String>> partOfRelations;
  private final int nodeCount;

  private EFOModel(Map<String, EFONode> nodes, Map<String, Set<String>> partOfRelations) {
    this.nodes = Map.copyOf(nodes); // Defensive, unmodifiable
    this.partOfRelations = Map.copyOf(partOfRelations);
    this.nodeCount = nodes.size();
  }

  /** Fluent builder for incremental population. */
  public static Builder builder() {
    return new Builder();
  }

  /** Stats helper. */
  public boolean isEmpty() {
    return nodeCount == 0;
  }

  public static class Builder {
    private final Map<String, EFONode> nodes = new HashMap<>();
    private final Map<String, Set<String>> partOfRelations = new HashMap<>();

    public Builder addNode(EFONode node) {
      nodes.put(node.getId(), node);
      return this;
    }

    public Builder addPartOf(String childId, String parentId) {
      partOfRelations.computeIfAbsent(childId, k -> new HashSet<>()).add(parentId);
      return this;
    }

    /**
     * Bulk-loads "part of" relations from reverse map.
     * reversePartOfMap: childId → [parentId1, parentId2, ...]
     */
    public Builder buildPartOfRelations(Map<String, Set<String>> reversePartOfMap) {
      for (Map.Entry<String, Set<String>> entry : reversePartOfMap.entrySet()) {
        String childId = entry.getKey();
        for (String parentId : entry.getValue()) {
          addPartOf(childId, parentId);
        }
      }
      return this;
    }

    /**
     * TEMP: Access during hierarchy linking (pre-build).
     * Mutable HashMap for addChild/addParent().
     */
    public Map<String, EFONode> getNodes() {
      return nodes;
    }

    public EFOModel build() {
      if (nodes.isEmpty()) {
        throw new IllegalStateException("Cannot build empty EFOModel");
      }
      return new EFOModel(nodes, partOfRelations);
    }
  }
}
