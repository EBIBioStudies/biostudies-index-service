package uk.ac.ebi.biostudies.index_service.index.efo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

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

  /** Retrieves terms for EFO ID based on flags. */
  public Set<String> getTerms(String efoId, int includeFlags) {
    if (efoId == null || includeFlags == 0) {
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
    if ((includeFlags & INCLUDE_SELF) != 0) {
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
