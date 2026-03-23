package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Provides ontology hierarchy lookups for EFO terms.
 *
 * <p>This service owns navigation over the EFO graph only. It resolves parent/child relations,
 * ancestor chains, and simple structural checks for autocomplete and tree rendering.
 */
@Service
@RequiredArgsConstructor
public class EFOHierarchyService {

  private final EFOTermMatcher efoTermMatcher;

  /**
   * Returns the primary EFO label for an EFO ID.
   *
   * @param efoId EFO identifier
   * @return canonical label, or null if not found
   */
  public String getTerm(String efoId) {
    if (efoId == null || efoId.isBlank()) {
      return null;
    }
    return efoTermMatcher.getTerm(efoId);
  }

  /**
   * Returns ancestor labels for a term, ordered from root to immediate parent.
   *
   * @param term EFO label
   * @return ancestor labels, or empty list if none
   */
  public List<String> getAncestors(String term) {
    if (term == null || term.isBlank()) {
      return Collections.emptyList();
    }
    return efoTermMatcher.getAncestors(term);
  }

  /**
   * Returns ancestor labels for an EFO ID.
   *
   * @param efoId EFO identifier
   * @return ancestor labels, or empty list if none
   */
  public List<String> getAncestorsByEfoId(String efoId) {
    String term = getTerm(efoId);
    return term == null ? Collections.emptyList() : getAncestors(term);
  }

  /**
   * Returns direct child labels for a term.
   *
   * @param term EFO label
   * @return direct children, or empty list if none
   */
  public List<String> getChildren(String term) {
    if (term == null || term.isBlank()) {
      return Collections.emptyList();
    }

    String efoId = efoTermMatcher.getEFOId(term);
    if (efoId == null) {
      return Collections.emptyList();
    }

    return getChildrenByEfoId(efoId);
  }

  /**
   * Returns direct child IDs for an EFO ID.
   *
   * @param efoId EFO identifier
   * @return direct child IDs, or empty set if none
   */
  public Set<String> getChildIds(String efoId) {
    if (efoId == null || efoId.isBlank()) {
      return Collections.emptySet();
    }

    return efoTermMatcher.getChildIds(efoId);
  }

  /**
   * Returns direct child labels for an EFO ID.
   *
   * @param efoId EFO identifier
   * @return direct children, or empty list if none
   */
  public List<String> getChildrenByEfoId(String efoId) {
    Set<String> childIds = getChildIds(efoId);
    if (childIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> children = new ArrayList<>(childIds.size());
    for (String childId : childIds) {
      String childTerm = efoTermMatcher.getTerm(childId);
      if (childTerm != null && !childTerm.isBlank()) {
        children.add(childTerm);
      }
    }
    return children;
  }

  /**
   * Checks whether a term has direct children.
   *
   * @param term EFO label
   * @return true if the term has children
   */
  public boolean hasChildren(String term) {
    return !getChildren(term).isEmpty();
  }

  /**
   * Checks whether an EFO ID has direct children.
   *
   * @param efoId EFO identifier
   * @return true if the term has children
   */
  public boolean hasChildrenByEfoId(String efoId) {
    return !getChildIds(efoId).isEmpty();
  }
}