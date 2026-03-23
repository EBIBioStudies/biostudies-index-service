package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.facet.FacetQuery;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Provides submission counts for EFO terms.
 *
 * <p>This service counts submissions from the submission index using the direct {@code efo_id}
 * values stored in each submission document. Hierarchy expansion is delegated to
 * {@link EFOHierarchyService}.
 *
 * <p>Counts are document counts, not facet-path counts. This matches the new indexing model where
 * submissions store only direct EFO matches.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EFOTermCountService {

  private final IndexManager indexManager;
  private final EFOTermMatcher efoTermMatcher;
  private final EFOHierarchyService efoHierarchyService;

  /**
   * Counts submissions that contain the exact EFO ID.
   *
   * @param efoId canonical EFO identifier
   * @return number of matching submissions, or 0 if the ID is null/blank or unavailable
   */
  public long countByEfoId(String efoId) {
    if (efoId == null || efoId.isBlank()) {
      return 0L;
    }

    try {
      return countDocuments(buildExactIdQuery(efoId));
    } catch (IOException e) {
      log.error("Failed to count submissions for EFO ID '{}': {}", efoId, e.getMessage(), e);
      return 0L;
    }
  }

  /**
   * Counts submissions that contain the exact EFO term.
   *
   * @param term EFO label
   * @return number of matching submissions, or 0 if the term is null/blank or unavailable
   */
  public long countByEfoTerm(String term) {
    if (term == null || term.isBlank()) {
      return 0L;
    }

    String efoId = efoTermMatcher.getEFOId(term);
    if (efoId == null || efoId.isBlank()) {
      return 0L;
    }

    return countByEfoId(efoId);
  }

  /**
   * Counts submissions that contain the EFO ID or any descendant IDs.
   *
   * @param efoId canonical EFO identifier
   * @return number of matching submissions, or 0 if unavailable
   */
  public long countIncludingDescendantsByEfoId(String efoId) {
    if (efoId == null || efoId.isBlank()) {
      return 0L;
    }

    Set<String> ids = collectDescendantIdsIncludingSelf(efoId);
    if (ids.isEmpty()) {
      return 0L;
    }

    try {
      return countDocuments(buildDisjunctionQuery(ids));
    } catch (IOException e) {
      log.error(
          "Failed to count submissions for EFO ID '{}' including descendants: {}",
          efoId,
          e.getMessage(),
          e);
      return 0L;
    }
  }

  /**
   * Counts submissions that contain the EFO term or any descendant terms.
   *
   * @param term EFO label
   * @return number of matching submissions, or 0 if unavailable
   */
  public long countIncludingDescendantsByEfoTerm(String term) {
    if (term == null || term.isBlank()) {
      return 0L;
    }

    String efoId = efoTermMatcher.getEFOId(term);
    if (efoId == null || efoId.isBlank()) {
      return 0L;
    }

    return countIncludingDescendantsByEfoId(efoId);
  }

  /**
   * Collects the given EFO ID and all descendant IDs reachable through the ontology hierarchy.
   *
   * @param efoId canonical EFO identifier
   * @return set containing the ID itself and all descendant IDs
   */
  public Set<String> collectDescendantIdsIncludingSelf(String efoId) {
    if (efoId == null || efoId.isBlank()) {
      return Collections.emptySet();
    }

    if (efoHierarchyService == null) {
      return Set.of(efoId);
    }

    Set<String> collected = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(efoId);

    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      if (current == null || current.isBlank() || !collected.add(current)) {
        continue;
      }

      for (String childId : efoHierarchyService.getChildIds(current)) {
        if (childId != null && !childId.isBlank()) {
          queue.addLast(childId);
        }
      }
    }

    return collected;
  }

  /**
   * Returns the primary label for an EFO ID.
   *
   * @param efoId canonical EFO identifier
   * @return primary term or null if unavailable
   */
  public String getTerm(String efoId) {
    return efoTermMatcher.getTerm(efoId);
  }

  /**
   * Returns whether the EFO ID has direct children.
   *
   * @param efoId canonical EFO identifier
   * @return true if the term has children
   */
  public boolean hasChildrenByEfoId(String efoId) {
    return efoHierarchyService.hasChildrenByEfoId(efoId);
  }

  /**
   * Counts submissions for a Lucene query against the submission index.
   *
   * @param query Lucene query
   * @return document count
   * @throws IOException if search fails
   */
  private long countDocuments(Query query) throws IOException {
    IndexSearcher searcher = indexManager.acquireSearcher(IndexName.SUBMISSION);
    try {
      return searcher.count(query);
    } finally {
      indexManager.releaseSearcher(IndexName.SUBMISSION, searcher);
    }
  }

  /**
   * Builds an exact match query for a single EFO ID.
   *
   * @param efoId canonical EFO identifier
   * @return Lucene query
   */
  private Query buildExactIdQuery(String efoId) {
    return new FacetQuery(EFOField.EFO_ID.getFieldName(), efoId);
  }

  /**
   * Builds a disjunction query over many EFO IDs.
   *
   * @param ids EFO identifiers to match
   * @return Lucene query matching any of the IDs
   */
  private Query buildDisjunctionQuery(Set<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return new FacetQuery(EFOField.EFO_ID.getFieldName(), "__no_such_efo_id__");
    }

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String id : ids) {
      if (id == null || id.isBlank()) {
        continue;
      }
      builder.add(buildExactIdQuery(id), BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }
}
