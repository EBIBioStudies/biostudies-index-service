package uk.ac.ebi.biostudies.index_service.search.taxonomy;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.autocomplete.EFOTermMatcher;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Searches EFO facets with hierarchical path encoding to provide term suggestions with submission
 * counts.
 *
 * <p>EFO terms are indexed as full hierarchical paths (e.g., "experimental factor/sample
 * factor/cell type") using SortedSetDocValuesFacetField. This class provides autocomplete
 * suggestions and hierarchy navigation with accurate submission counts.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>
 * // User types "odonto"
 * List&lt;TaxonomyNode&gt; results = taxonomySearcher.searchAllDepths("odonto", 10);
 * // Returns: [TaxonomyNode(term="odontoclast", count=2, ...)]
 *
 * // User expands "phagocyte"
 * List&lt;TaxonomyNode&gt; children = taxonomySearcher.getChildren("phagocyte", 10);
 * // Returns: [
 * //   TaxonomyNode(term="osteoclast", count=45, hasChildren=true),
 * //   TaxonomyNode(term="macrophage", count=105, hasChildren=false)
 * // ]
 * </pre>
 */
@Slf4j
@Service
public class TaxonomySearcher {

  private static final int MAX_FACET_RESULTS = 10000;

  private final IndexManager indexManager;
  private final TaxonomyManager taxonomyManager;
  private final EFOTermMatcher efoTermMatcher;

  public TaxonomySearcher(
      IndexManager indexManager, TaxonomyManager taxonomyManager, EFOTermMatcher efoTermMatcher) {
    this.indexManager = indexManager;
    this.taxonomyManager = taxonomyManager;
    this.efoTermMatcher = efoTermMatcher;
  }

  /**
   * Searches for EFO terms at any depth matching the query prefix with submission counts.
   *
   * <p>Searches across all hierarchy levels for terms matching the given prefix. The search matches
   * against the actual term name (last segment of the path), not the full hierarchical path. If the
   * same term appears at multiple locations in the hierarchy, counts are aggregated.
   *
   * <p><b>Example:</b> User searches for "cell" and the index contains:
   *
   * <ul>
   *   <li>"experimental factor/sample factor/cell type" (count=5)
   *   <li>"experimental factor/process/cellular process" (count=3)
   * </ul>
   *
   * Results will show:
   *
   * <ul>
   *   <li>"cell type" with count=5
   *   <li>"cellular process" with count=3
   * </ul>
   *
   * @param termPrefix the term prefix to search for (case-insensitive, leading/trailing whitespace
   *     ignored)
   * @param maxResults maximum number of results to return (must be &gt; 0)
   * @return list of taxonomy nodes with counts, ordered by term label (alphabetical); empty list if
   *     no matches or invalid input
   * @throws IOException if index access fails
   */
  public List<TaxonomyNode> searchAllDepths(String termPrefix, int maxResults) throws IOException {
    if (termPrefix == null || termPrefix.trim().isEmpty()) {
      log.debug("Ignoring null or empty term prefix");
      return List.of();
    }

    if (maxResults <= 0) {
      log.debug("Invalid maxResults: {}, returning empty list", maxResults);
      return List.of();
    }

    IndexSearcher searcher = indexManager.acquireSearcher(IndexName.SUBMISSION);

    try {
      FacetsConfig config = taxonomyManager.getFacetsConfig();
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), config);

      FacetsCollector fc = new FacetsCollector();
      searcher.search(new MatchAllDocsQuery(), fc);

      Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
      FacetResult result = facets.getTopChildren(MAX_FACET_RESULTS, "efo");

      if (result == null || result.labelValues.length == 0) {
        log.debug("No EFO facets found in index");
        return List.of();
      }

      log.debug("Total EFO facets found: {}", result.labelValues.length);

      String termLower = termPrefix.trim().toLowerCase();
      Map<String, Integer> matchingTerms = new LinkedHashMap<>();

      for (LabelAndValue lv : result.labelValues) {
        String facetPath = lv.label; // Format: "ancestor1/ancestor2/.../term"

        // Extract the actual term (last segment of path)
        String term = extractLastSegment(facetPath);

        // Match against the actual term name, not the full path
        if (term.toLowerCase().startsWith(termLower)) {
          // Aggregate counts if same term appears in multiple paths
          matchingTerms.merge(term, (int) lv.value.longValue(), Integer::sum);
        }
      }

      log.debug("Found {} EFO terms matching prefix '{}'", matchingTerms.size(), termPrefix);

      return matchingTerms.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .limit(maxResults)
          .map(
              e -> {
                String term = e.getKey();
                int count = e.getValue();
                String efoId = efoTermMatcher.getEFOId(term);

                // Check if this term has children in the facets
                boolean hasChildren = checkIfTermHasChildren(result.labelValues, term);

                log.debug(
                    "Term '{}': count={}, efoId='{}', hasChildren={}",
                    term,
                    count,
                    efoId != null ? efoId : "null",
                    hasChildren);

                return new TaxonomyNode(term, count, efoId, hasChildren);
              })
          .collect(Collectors.toList());

    } finally {
      indexManager.releaseSearcher(IndexName.SUBMISSION, searcher);
    }
  }

  /**
   * Checks if a term has any children by finding all paths ending with the term and checking if any
   * deeper paths exist.
   *
   * @param allFacets all available facet label/value pairs
   * @param term the term to check (e.g., "cell type", "leukocyte")
   * @return true if the term has children in the indexed facets
   */
  private boolean checkIfTermHasChildren(LabelAndValue[] allFacets, String term) {
    // First, find all paths where this term appears as the last segment
    List<String> termPaths = new ArrayList<>();

    for (LabelAndValue lv : allFacets) {
      String lastSegment = extractLastSegment(lv.label);

      if (lastSegment.equalsIgnoreCase(term)) {
        termPaths.add(lv.label);
      }
    }

    // If term doesn't exist in any path, it has no children
    if (termPaths.isEmpty()) {
      return false;
    }

    // Check if any facet path extends beyond any of the term's paths
    for (String termPath : termPaths) {
      String childPrefix = termPath + "/";

      for (LabelAndValue lv : allFacets) {
        if (lv.label.startsWith(childPrefix)) {
          return true; // Found a child path
        }
      }
    }

    return false; // No children found
  }

  /**
   * Gets children of a specific EFO term with submission counts.
   *
   * <p>Uses the submission index facet structure to identify direct children by looking for terms
   * that appear immediately after the parent in hierarchical paths.
   *
   * <p>For each child, the count represents the number of unique submissions containing that child
   * term or any of its descendants. The hasChildren flag is set to true only if the child has
   * grandchildren that appear in actual submissions, ensuring the UI only shows expand buttons when
   * there is data to display.
   *
   * <p>Facets are stored as full paths (e.g., "experimental factor/sample factor/cell type"),
   * enabling accurate parent-child relationship queries without depth conflicts.
   *
   * <p><b>Algorithm:</b>
   *
   * <ol>
   *   <li>Find all facet paths where parent term appears as last segment
   *   <li>For each parent path, find facet paths that extend it by one level
   *   <li>Extract immediate child term names (next segment after parent)
   *   <li>Aggregate counts across paths (using max to avoid double-counting)
   *   <li>Check if each child has grandchildren in facets to set hasChildren flag
   * </ol>
   *
   * @param parentTerm the parent EFO term (e.g., "cell type", "leukocyte")
   * @param maxResults maximum number of children to return (must be &gt; 0)
   * @return list of child taxonomy nodes with counts and hasChildren flags, ordered by term label;
   *     empty list if no children or invalid input
   * @throws IOException if index access fails
   */
  public List<TaxonomyNode> getChildren(String parentTerm, int maxResults) throws IOException {
    if (parentTerm == null || parentTerm.trim().isEmpty()) {
      log.debug("Ignoring null or empty parent term");
      return List.of();
    }

    if (maxResults <= 0) {
      log.debug("Invalid maxResults: {}, returning empty list", maxResults);
      return List.of();
    }

    log.debug("Getting children for term '{}' from submission facets", parentTerm);

    IndexSearcher searcher = indexManager.acquireSearcher(IndexName.SUBMISSION);

    try {
      FacetsConfig config = taxonomyManager.getFacetsConfig();
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(searcher.getIndexReader(), config);

      FacetsCollector fc = new FacetsCollector();
      searcher.search(new MatchAllDocsQuery(), fc);

      Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
      FacetResult result = facets.getTopChildren(MAX_FACET_RESULTS, "efo");

      if (result == null || result.labelValues.length == 0) {
        log.debug("No EFO facets found in index");
        return List.of();
      }

      // Find all facet paths where parent term appears
      List<String> matchingPaths = findAllPathsContainingTerm(result.labelValues, parentTerm);

      if (matchingPaths.isEmpty()) {
        log.debug("No facet paths contain term '{}'", parentTerm);
        return List.of();
      }

      log.debug("Found {} facet paths containing '{}'", matchingPaths.size(), parentTerm);

      // Find children across all matching paths
      Map<String, Integer> childCounts = new LinkedHashMap<>();

      for (String parentPath : matchingPaths) {
        String parentPathPrefix = parentPath + "/";

        for (LabelAndValue lv : result.labelValues) {
          String facetPath = lv.label;

          if (facetPath.startsWith(parentPathPrefix)) {
            String remainder = facetPath.substring(parentPathPrefix.length());

            // Get just the first segment (immediate child)
            String child =
                remainder.contains("/")
                    ? remainder.substring(0, remainder.indexOf("/"))
                    : remainder;

            // Use max to avoid double-counting (all paths in same doc have same count)
            childCounts.merge(child, (int) lv.value.longValue(), Integer::max);

            if (log.isDebugEnabled()) {
              log.debug("  Found child '{}' from path '{}'", child, facetPath);
            }
          }
        }
      }

      log.debug("Found {} unique children for '{}'", childCounts.size(), parentTerm);

      // Build result with hasChildren flag
      return childCounts.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .limit(maxResults)
          .map(
              e -> {
                String childTerm = e.getKey();
                int count = e.getValue();
                String efoId = efoTermMatcher.getEFOId(childTerm);

                // Check if child has grandchildren
                List<String> childPaths = findAllPathsContainingTerm(result.labelValues, childTerm);
                boolean hasChildren =
                    childPaths.stream()
                        .anyMatch(childPath -> hasChildrenInFacets(result.labelValues, childPath));

                return new TaxonomyNode(childTerm, count, efoId, hasChildren);
              })
          .collect(Collectors.toList());

    } finally {
      indexManager.releaseSearcher(IndexName.SUBMISSION, searcher);
    }
  }

  /**
   * Finds all facet paths where the term appears as the last segment. This handles cases where a
   * term appears in multiple branches of the ontology hierarchy.
   *
   * @param allFacets all available facet label/value pairs
   * @param term the term to find (case-insensitive)
   * @return list of all facet paths ending with the term; empty list if term not found
   */
  private List<String> findAllPathsContainingTerm(LabelAndValue[] allFacets, String term) {
    List<String> paths = new ArrayList<>();

    for (LabelAndValue lv : allFacets) {
      String lastSegment = extractLastSegment(lv.label);

      // Case-insensitive comparison
      if (lastSegment.equalsIgnoreCase(term)) {
        paths.add(lv.label);
      }
    }

    return paths;
  }

  /**
   * Checks if a term path has any children by looking for facet paths that extend beyond it.
   *
   * @param allFacets all available facet label/value pairs
   * @param termPath the full path of the term to check
   * @return true if at least one child path exists
   */
  private boolean hasChildrenInFacets(LabelAndValue[] allFacets, String termPath) {
    String searchPrefix = termPath + "/";

    for (LabelAndValue lv : allFacets) {
      if (lv.label.startsWith(searchPrefix)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Extracts the last segment from a facet path.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"experimental factor/sample factor/cell type" → "cell type"
   *   <li>"organism" → "organism"
   * </ul>
   *
   * @param path the facet path
   * @return the last segment (term name)
   */
  private String extractLastSegment(String path) {
    int lastSlash = path.lastIndexOf('/');
    return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
  }

  /**
   * Formats taxonomy nodes as pipe-delimited autocomplete response with EFO IDs and counts.
   *
   * <p>Each line follows the format: {@code term|o|efoId|count} where:
   *
   * <ul>
   *   <li>'o' indicates ontology term (for UI compatibility)
   *   <li>'efoId' is the unique EFO URL identifier (empty if node has no children)
   *   <li>'count' is the number of submissions containing this term or its descendants
   * </ul>
   *
   * <p>The efoId field is left empty for leaf nodes (terms with no children) to signal to the
   * frontend that no expand button should be shown, matching legacy autocomplete behavior.
   *
   * @param nodes the taxonomy nodes to format
   * @return formatted response string with escaped newlines (\n), one line per node; empty string
   *     if nodes is null or empty
   */
  public String formatAsAutocompleteResponse(List<TaxonomyNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return "";
    }

    StringBuilder result = new StringBuilder();

    for (TaxonomyNode node : nodes) {
      String line = node.term() + "|o|"
          + (node.hasChildren() && node.efoId() != null ? node.efoId() : "")
          + "|"
          + node.count();

      log.debug("Autocomplete line: [{}]", line);  // ← Add this

      result.append(line).append("\n");
    }

    String response = result.toString();
    log.debug("Full autocomplete response:\n{}", response);  // ← And this

    return response;
  }


  /**
   * Gets children of a specific EFO term by EFO ID with submission counts.
   *
   * <p>Converts the EFO URI to its term label, then delegates to {@link #getChildren(String, int)}.
   *
   * @param efoId the EFO URI (e.g., "http://purl.obolibrary.org/obo/CL_0000000")
   * @param maxResults maximum number of children to return (must be &gt; 0)
   * @return list of child taxonomy nodes with counts and hasChildren flags; empty list if EFO ID is
   *     invalid or term not found
   * @throws IOException if index access fails
   */
  public List<TaxonomyNode> getChildrenByEfoId(String efoId, int maxResults) throws IOException {
    if (efoId == null || efoId.trim().isEmpty()) {
      log.debug("Ignoring null or empty EFO ID");
      return List.of();
    }

    // Convert EFO ID to term using existing method
    String parentTerm = efoTermMatcher.getTerm(efoId);

    if (parentTerm == null) {
      log.debug("No term found for EFO ID '{}'", efoId);
      return List.of();
    }

    log.debug("Resolved EFO ID '{}' to term '{}'", efoId, parentTerm);

    // Use existing getChildren method
    return getChildren(parentTerm, maxResults);
  }
}
