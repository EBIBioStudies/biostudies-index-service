package uk.ac.ebi.biostudies.index_service.index.efo;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Represents a node in the Experimental Factor Ontology (EFO) graph. Supports hierarchical
 * traversal for term expansion in search/indexing. Children and parents are maintained as sorted
 * sets by term lexicographically for consistent ordering (e.g., UI facets).
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class EFONode {

  /**
   * Comparator sorts nodes by term (null/empty as "") lexicographically. Ensures stable ordering in
   * TreeSets.
   */
  private static final Comparator<EFONode> TERM_COMPARATOR =
      Comparator.comparing(node -> node == null || node.getTerm() == null ? "" : node.getTerm());

  @EqualsAndHashCode.Include @ToString.Include private final String id;

  @ToString.Include private String term;

  private final SortedSet<EFONode> children;
  private final SortedSet<EFONode> parents;

  private String efoUri;
  private Set<String> alternativeTerms = new HashSet<>();
  private Boolean isOrganizationalClass;

  /**
   * Constructs immutable ID and term; initializes mutable collections. Callers must populate
   * children/parents/alts post-construction.
   */
  public EFONode(String id, String term) {
    this.id = Objects.requireNonNull(id, "ID must not be null");
    this.term = term;
    this.children = new TreeSet<>(TERM_COMPARATOR);
    this.parents = new TreeSet<>(TERM_COMPARATOR);
  }

  /** Defensive getter: always returns non-null set. */
  public Set<String> getAlternativeTerms() {
    if (alternativeTerms == null) {
      alternativeTerms = new HashSet<>();
    }
    return alternativeTerms;
  }

  /** True if this node represents an organizational (non-leaf) class. */
  public boolean isOrganizationalClass() {
    return Boolean.TRUE.equals(isOrganizationalClass);
  }

  /** True if node has child terms (for recursion optimization). */
  public boolean hasChildren() {
    return !children.isEmpty();
  }

  /** Adds a child node (idempotent, sorted). */
  public void addChild(EFONode child) {
    children.add(Objects.requireNonNull(child, "Child must not be null"));
  }

  /** Adds a parent node (idempotent, sorted). */
  public void addParent(EFONode parent) {
    parents.add(Objects.requireNonNull(parent, "Parent must not be null"));
  }

  public void setTerm(String term) {
    this.term = term;
  }
}
