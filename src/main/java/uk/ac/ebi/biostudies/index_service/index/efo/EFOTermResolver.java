package uk.ac.ebi.biostudies.index_service.index.efo;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

/**
 * Queries EFO for term expansion using a loaded {@link EFOModel}. Supports recursive retrieval via
 * bit flags. Immutable, thread-safe.
 *
 * @see EFOModel
 * @see EFONode
 */
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EFOTermResolver {

  // Bit flags for term inclusion
  public static final int INCLUDE_SELF = 1;
  public static final int INCLUDE_ALT_TERMS = 2;
  public static final int INCLUDE_CHILD_TERMS = 4;
  public static final int INCLUDE_CHILD_ALT_TERMS = 8;
  public static final int INCLUDE_PART_OF_TERMS = 16;

  public static final int INCLUDE_CHILDREN =
      INCLUDE_CHILD_TERMS | INCLUDE_CHILD_ALT_TERMS | INCLUDE_PART_OF_TERMS;

  public static final String ROOT_ID = "http://www.ebi.ac.uk/efo/EFO_0000001";

  private final EFOModel model;
  private final String versionInfo;

  /** Creates resolver from loaded model. */
  public EFOTermResolver(EFOModel model, String versionInfo) {
    this.model = model;
    this.versionInfo = versionInfo;
  }

  /** Access to underlying model. */
  public EFOModel getModel() {
    return model;
  }

  /** EFO version info. */
  public String getVersionInfo() {
    return versionInfo;
  }

  /**
   * Returns the primary term for an EFO ID.
   *
   * @param efoId EFO identifier
   * @return primary term, or null if not found
   */
  public String getTerm(String efoId) {
    if (efoId == null || model == null) {
      return null;
    }
    EFONode node = model.getNodes().get(efoId);
    return node != null ? node.getTerm() : null;
  }

  /**
   * Returns direct child IDs for an EFO ID.
   *
   * @param efoId EFO identifier
   * @return direct child IDs, or empty set if none
   */
  public Set<String> getChildIds(String efoId) {
    if (efoId == null || model == null) {
      return Collections.emptySet();
    }

    EFONode node = model.getNodes().get(efoId);
    if (node == null || !node.hasChildren()) {
      return Collections.emptySet();
    }

    return node.getChildren().stream()
        .map(EFONode::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Returns direct child terms for an EFO ID.
   *
   * @param efoId EFO identifier
   * @return direct child terms, or empty set if none
   */
  public Set<String> getChildTerms(String efoId) {
    if (efoId == null || model == null) {
      return Collections.emptySet();
    }

    EFONode node = model.getNodes().get(efoId);
    if (node == null || !node.hasChildren()) {
      return Collections.emptySet();
    }

    return node.getChildren().stream()
        .map(EFONode::getTerm)
        .filter(term -> term != null && !term.isBlank())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Checks whether an EFO ID has direct children.
   *
   * @param efoId EFO identifier
   * @return true if the node has children
   */
  public boolean hasChildren(String efoId) {
    return !getChildIds(efoId).isEmpty();
  }

  /** Retrieves terms for EFO ID based on flags. */
  public Set<String> getTerms(String efoId, int includeFlags) {
    if (efoId == null || includeFlags == 0 || model == null) {
      return Collections.emptySet();
    }
    EFONode node = model.getNodes().get(efoId);
    return getTerms(node, includeFlags);
  }

  /** Recursive collector (optimized). */
  private Set<String> getTerms(EFONode node, int includeFlags) {
    Set<String> terms = new HashSet<>();
    if (node == null) return terms;

    // Self + alts
    if ((includeFlags & INCLUDE_SELF) != 0 && node.getTerm() != null) {
      terms.add(node.getTerm());
    }
    if ((includeFlags & INCLUDE_ALT_TERMS) != 0) {
      terms.addAll(node.getAlternativeTerms());
    }

    // Children
    if ((includeFlags & INCLUDE_CHILD_TERMS) != 0 && node.hasChildren()) {
      int childFlags =
          includeFlags
              | INCLUDE_SELF
              | ((includeFlags & INCLUDE_CHILD_ALT_TERMS) != 0 ? INCLUDE_ALT_TERMS : 0);
      node.getChildren().forEach(child -> terms.addAll(getTerms(child, childFlags)));
    }

    // Part-of (parents)
    if ((includeFlags & INCLUDE_PART_OF_TERMS) != 0) {
      Set<String> partOfIds = model.getPartOfRelations().get(node.getId());
      if (partOfIds != null) {
        int partOfFlags =
            includeFlags
                | INCLUDE_SELF
                | ((includeFlags & INCLUDE_CHILD_ALT_TERMS) != 0 ? INCLUDE_ALT_TERMS : 0);
        partOfIds.stream()
            .map(model.getNodes()::get)
            .forEach(partOfNode -> terms.addAll(getTerms(partOfNode, partOfFlags)));
      }
    }

    return terms;
  }
}
