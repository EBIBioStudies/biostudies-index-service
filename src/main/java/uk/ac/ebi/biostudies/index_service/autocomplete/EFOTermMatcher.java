package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.Bits;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.search.searchers.EFOSearcher;

/**
 * Provides fast EFO term matching and hierarchy resolution for autocomplete and indexing.
 *
 * <p>Loads all EFO terms and their hierarchical relationships into memory at startup by querying
 * the EFO Lucene index. This avoids re-parsing the efo.owl file on every startup and enables O(1)
 * term lookups.
 *
 * <p><b>Performance:</b>
 *
 * <ul>
 *   <li>Initialization: ~500-1000ms for ~60k EFO documents
 *   <li>Term lookups: O(1)
 *   <li>Memory: ~4-6 MB
 * </ul>
 *
 * <p><b>Thread Safety:</b> All caches use concurrent collections and are safe for concurrent reads
 * after initialization.
 */
@Slf4j
@Component
public class EFOTermMatcher {

  private static final int PAGE_SIZE = 1000;
  private static final int LOG_PROGRESS_INTERVAL = 10000;

  private final EFOSearcher efoSearcher;
  private final IndexManager indexManager;

  // Cache: term (lowercase) -> EFO ID
  private Map<String, String> termToIdCache;

  // Cache: term (lowercase) -> list of ancestor terms (root to immediate parent)
  private Map<String, List<String>> termToAncestorsCache;

  // Cache: EFO ID -> term (original case)
  private Map<String, String> idToTermCache;

  // All unique terms including alternatives (lowercase, for fast matching)
  private Set<String> allTermsLowercase;

  public EFOTermMatcher(EFOSearcher efoSearcher, IndexManager indexManager) {
    this.efoSearcher = Objects.requireNonNull(efoSearcher, "efoSearcher must not be null");
    this.indexManager = Objects.requireNonNull(indexManager, "indexManager must not be null");
  }

  /**
   * Initializes EFO term caches by querying the EFO Lucene index.
   *
   * <p>This method should be called during application initialization after the EFO index is ready.
   * It reads from the existing EFO index (not the efo.owl file), making it fast even on regular
   * startups.
   *
   * @throws IllegalStateException if initialization fails
   */
  public void initialize() {
    log.info("Initializing EFO term matcher caches from index...");

    try {
      long startTime = System.currentTimeMillis();

      loadAllEFOTermsFromIndex();

      long duration = System.currentTimeMillis() - startTime;

      log.info(
          "EFO term matcher initialized: {} unique terms, {} with hierarchies ({}ms)",
          allTermsLowercase.size(),
          termToAncestorsCache.size(),
          duration);

    } catch (Exception e) {
      log.error("Failed to initialize EFO term matcher", e);
      throw new IllegalStateException("EFO term matcher initialization failed", e);
    }
  }

  /**
   * Loads all EFO terms and parent relationships by directly reading the index. Much faster than
   * using the search API since it bypasses document mapping.
   *
   * @throws IOException if index access fails
   */
  /**
   * Loads all EFO terms and parent relationships by directly reading the index. Much faster than
   * using the search API since it bypasses document mapping.
   *
   * @throws IOException if index access fails
   */
  /**
   * Loads all EFO terms and parent relationships by directly reading the index. Much faster than
   * using the search API since it bypasses document mapping.
   *
   * @throws IOException if index access fails
   */
  /**
   * Loads all EFO terms and parent relationships by directly reading the index. Much faster than
   * using the search API since it bypasses document mapping.
   *
   * @throws IOException if index access fails
   */
  private void loadAllEFOTermsFromIndex() throws IOException {
    // Initialize concurrent collections
    termToIdCache = new ConcurrentHashMap<>();
    idToTermCache = new ConcurrentHashMap<>();
    allTermsLowercase = ConcurrentHashMap.newKeySet();
    Map<String, List<String>> nodeParents = new ConcurrentHashMap<>();

    int primaryTermCount = 0;
    int altTermCount = 0;

    log.debug("Loading EFO terms directly from index reader");

    // Get direct access to the index
    IndexSearcher searcher = indexManager.acquireSearcher(IndexName.EFO);

    try {
      IndexReader reader = searcher.getIndexReader();
      StoredFields storedFields = searcher.storedFields();

      int maxDoc = reader.maxDoc();

      log.debug("Found {} documents in EFO index", maxDoc);

      // Iterate through all documents
      for (int docId = 0; docId < maxDoc; docId++) {
        // Skip deleted documents
        Bits liveDocs = MultiBits.getLiveDocs(reader);
        if (liveDocs != null && !liveDocs.get(docId)) {
          continue;
        }

        Document doc = storedFields.document(docId);

        // Extract ID field
        String efoId = doc.get(EFOField.ID.getFieldName());

        // Extract primary term
        String term = doc.get(EFOField.TERM.getFieldName());
        if (term != null && efoId != null) {
          String termLower = term.toLowerCase();
          termToIdCache.put(termLower, efoId);
          idToTermCache.put(efoId, term);
          allTermsLowercase.add(termLower);
          primaryTermCount++;

          // Extract parent relationships
          String[] parents = doc.getValues(EFOField.PARENT.getFieldName());
          if (parents != null && parents.length > 0) {
            nodeParents.put(efoId, Arrays.asList(parents));
          }
        }

        // Extract alternative term
        String altTerm = doc.get(EFOField.ALTERNATIVE_TERMS.getFieldName());
        if (altTerm != null) {
          String altTermLower = altTerm.toLowerCase();
          allTermsLowercase.add(altTermLower);

          if (efoId != null) {
            termToIdCache.putIfAbsent(altTermLower, efoId);
          }

          altTermCount++;
        }

        // Log progress
        if ((docId + 1) % LOG_PROGRESS_INTERVAL == 0) {
          log.debug("Processed {} / {} documents", docId + 1, maxDoc);
        }
      }

      log.debug(
          "Loaded {} primary terms, {} alternative terms, and {} parent relationships from {} documents",
          primaryTermCount,
          altTermCount,
          nodeParents.size(),
          maxDoc);

    } finally {
      indexManager.releaseSearcher(IndexName.EFO, searcher);
    }

    // Build ancestor chains from collected parent relationships
    buildAncestorChainsFromParentMap(nodeParents);
  }

  /**
   * Builds ancestor chains from parent relationship map using memoization.
   *
   * <p>Time complexity: O(N) where N is the number of nodes, as each node is visited exactly once
   * due to caching.
   *
   * @param nodeParents map of node ID to list of parent IDs
   */
  private void buildAncestorChainsFromParentMap(Map<String, List<String>> nodeParents) {
    termToAncestorsCache = new ConcurrentHashMap<>();
    Map<String, List<String>> ancestorCache = new ConcurrentHashMap<>();

    int nodesWithAncestors = 0;

    for (Map.Entry<String, String> entry : idToTermCache.entrySet()) {
      String efoId = entry.getKey();
      String term = entry.getValue();

      List<String> ancestors = computeAncestorsWithMemoization(efoId, nodeParents, ancestorCache);

      if (!ancestors.isEmpty()) {
        termToAncestorsCache.put(term.toLowerCase(), ancestors);
        nodesWithAncestors++;
      }
    }

    log.debug("Built ancestor chains for {} terms", nodesWithAncestors);
  }

  /**
   * Recursively computes ancestors with memoization to avoid redundant computation.
   *
   * <p>Each node is processed exactly once due to caching. The EFO ontology is primarily
   * tree-structured, though it's technically a DAG. This method takes the first parent when
   * multiple parents exist.
   *
   * @param efoId the EFO ID to compute ancestors for
   * @param nodeParents map of node ID to parent IDs
   * @param cache memoization cache to avoid recomputation
   * @return list of ancestor terms ordered from root to immediate parent
   */
  private List<String> computeAncestorsWithMemoization(
      String efoId, Map<String, List<String>> nodeParents, Map<String, List<String>> cache) {

    // Check cache first (memoization)
    if (cache.containsKey(efoId)) {
      return new ArrayList<>(cache.get(efoId)); // Return copy to prevent mutation
    }

    List<String> ancestors = new ArrayList<>();
    List<String> parents = nodeParents.get(efoId);

    if (parents == null || parents.isEmpty()) {
      // Root node - no ancestors
      cache.put(efoId, ancestors);
      return ancestors;
    }

    // Take first parent (EFO is primarily tree-structured)
    String parentId = parents.get(0);
    String parentTerm = idToTermCache.get(parentId);

    if (parentTerm != null) {
      // Recursively get parent's ancestors (memoized)
      List<String> parentAncestors = computeAncestorsWithMemoization(parentId, nodeParents, cache);

      // Build this node's ancestors: parent's ancestors + parent itself
      ancestors.addAll(parentAncestors);
      ancestors.add(parentTerm);
    }

    // Cache the result
    cache.put(efoId, new ArrayList<>(ancestors));
    return ancestors;
  }

  /**
   * Finds all EFO terms present in the given content string.
   *
   * <p>Uses word boundary matching to avoid partial matches (e.g., "phagocyte" won't match
   * "macrophagocyte").
   *
   * <p><b>Performance:</b> O(T × C) where T = number of EFO terms (~15k), C = content length.
   * Typical execution: 10-30ms for average submission content.
   *
   * @param content the text to search for EFO terms
   * @return list of matched EFO terms in original case (empty if content is null/empty)
   */
  public List<String> findEFOTerms(String content) {
    if (content == null || content.isEmpty()) {
      return Collections.emptyList();
    }

    String contentLower = content.toLowerCase();
    List<String> matches = new ArrayList<>();

    for (String termLower : allTermsLowercase) {
      // Use word boundary regex for accurate matching
      if (Pattern.compile("\\b" + Pattern.quote(termLower) + "\\b").matcher(contentLower).find()) {

        // Get original case term via ID lookup
        String efoId = termToIdCache.get(termLower);
        if (efoId != null) {
          String originalTerm = idToTermCache.get(efoId);
          if (originalTerm != null) {
            matches.add(originalTerm);
          }
        } else {
          // Alternative term without ID - add as lowercase
          matches.add(termLower);
        }
      }
    }

    return matches;
  }

  /**
   * Gets the ancestor chain for a given term.
   *
   * @param term the EFO term (case-insensitive)
   * @return list of ancestor terms ordered from root to immediate parent, or empty list if term has
   *     no ancestors or is not found
   */
  public List<String> getAncestors(String term) {
    if (term == null) {
      return Collections.emptyList();
    }
    return termToAncestorsCache.getOrDefault(term.toLowerCase(), Collections.emptyList());
  }

  /**
   * Gets the EFO ID (URI) for a given term.
   *
   * @param term the EFO term (case-insensitive)
   * @return the EFO ID (URI), or null if term not found
   */
  public String getEFOId(String term) {
    return term != null ? termToIdCache.get(term.toLowerCase()) : null;
  }

  /**
   * Gets the primary term for a given EFO ID.
   *
   * @param efoId the EFO ID (URI)
   * @return the primary term in original case, or null if ID not found
   */
  public String getTerm(String efoId) {
    return efoId != null ? idToTermCache.get(efoId) : null;
  }

  /**
   * Checks if a term exists in the EFO ontology.
   *
   * @param term the term to check (case-insensitive)
   * @return true if term exists as primary or alternative term
   */
  public boolean isEFOTerm(String term) {
    return term != null && allTermsLowercase.contains(term.toLowerCase());
  }

  /**
   * Gets all unique EFO terms (for testing/debugging).
   *
   * @return unmodifiable set of all lowercase terms
   */
  public Set<String> getAllTerms() {
    return Collections.unmodifiableSet(allTermsLowercase);
  }

  /**
   * Gets cache statistics for monitoring.
   *
   * @return formatted string with cache sizes
   */
  public String getCacheStats() {
    return String.format(
        "EFOTermMatcher[terms=%d, withHierarchy=%d, nodes=%d]",
        allTermsLowercase.size(), termToAncestorsCache.size(), idToTermCache.size());
  }
}
