package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
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

/**
 * Provides fast EFO term matching and hierarchy resolution for autocomplete and indexing.
 *
 * <p>Loads all EFO terms and their hierarchical relationships into memory at startup by querying
 * the EFO Lucene index, enabling O(1) term lookups. Thread-safe for concurrent reads after
 * initialization.
 */
@Slf4j
@Component
public class EFOTermMatcher {

  private static final int LOG_PROGRESS_INTERVAL = 10000;

  private final IndexManager indexManager;

  /** Maps lowercase terms to their EFO IDs. */
  private Map<String, String> termToIdCache;

  /** Maps lowercase terms to their ancestor chains (root to immediate parent). */
  private Map<String, List<String>> termToAncestorsCache;

  /** Maps EFO IDs to their primary terms in original case. */
  private Map<String, String> idToTermCache;

  /** Contains all unique terms including alternatives in lowercase. */
  private Set<String> allTermsLowercase;

  public EFOTermMatcher(IndexManager indexManager) {
    this.indexManager = Objects.requireNonNull(indexManager, "indexManager must not be null");
  }

  /**
   * Initializes EFO term caches by querying the EFO Lucene index.
   *
   * <p>Should be called during application initialization after the EFO index is ready.
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
   * Loads all EFO terms and parent relationships by directly reading the index.
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

    IndexSearcher searcher = indexManager.acquireSearcher(IndexName.EFO);

    try {
      IndexReader reader = searcher.getIndexReader();
      StoredFields storedFields = searcher.storedFields();

      int maxDoc = reader.maxDoc();

      log.debug("Found {} documents in EFO index", maxDoc);

      for (int docId = 0; docId < maxDoc; docId++) {
        Bits liveDocs = MultiBits.getLiveDocs(reader);
        if (liveDocs != null && !liveDocs.get(docId)) {
          continue;
        }

        Document doc = storedFields.document(docId);

        String efoId = doc.get(EFOField.ID.getFieldName());
        String term = doc.get(EFOField.TERM.getFieldName());

        if (term != null && efoId != null) {
          String termLower = term.toLowerCase();
          termToIdCache.put(termLower, efoId);
          idToTermCache.put(efoId, term);
          allTermsLowercase.add(termLower);
          primaryTermCount++;

          String[] parents = doc.getValues(EFOField.PARENT.getFieldName());
          if (parents != null && parents.length > 0) {
            nodeParents.put(efoId, Arrays.asList(parents));
          }
        }

        String altTerm = doc.get(EFOField.ALTERNATIVE_TERMS.getFieldName());
        if (altTerm != null) {
          String altTermLower = altTerm.toLowerCase();
          allTermsLowercase.add(altTermLower);

          if (efoId != null) {
            termToIdCache.putIfAbsent(altTermLower, efoId);
          }

          altTermCount++;
        }

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

    buildAncestorChainsFromParentMap(nodeParents);
  }

  /**
   * Builds ancestor chains from parent relationship map using memoization.
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
   * <p>Takes the first parent when multiple parents exist, treating the EFO ontology as
   * tree-structured.
   *
   * @param efoId the EFO ID to compute ancestors for
   * @param nodeParents map of node ID to parent IDs
   * @param cache memoization cache to avoid recomputation
   * @return list of ancestor terms ordered from root to immediate parent
   */
  private List<String> computeAncestorsWithMemoization(
      String efoId, Map<String, List<String>> nodeParents, Map<String, List<String>> cache) {

    if (cache.containsKey(efoId)) {
      return new ArrayList<>(cache.get(efoId));
    }

    List<String> ancestors = new ArrayList<>();
    List<String> parents = nodeParents.get(efoId);

    if (parents == null || parents.isEmpty()) {
      cache.put(efoId, ancestors);
      return ancestors;
    }

    String parentId = parents.get(0);
    String parentTerm = idToTermCache.get(parentId);

    if (parentTerm != null) {
      List<String> parentAncestors = computeAncestorsWithMemoization(parentId, nodeParents, cache);
      ancestors.addAll(parentAncestors);
      ancestors.add(parentTerm);
    }

    cache.put(efoId, new ArrayList<>(ancestors));
    return ancestors;
  }

  /**
   * Finds all EFO terms present in the given content string using word boundary matching.
   *
   * <p>When multiple overlapping terms match, prefers longer terms. Deduplicates results when
   * alternative terms map to the same primary term.
   *
   * @param content the text to search for EFO terms
   * @return list of matched EFO terms in original case (empty if content is null/empty)
   */
  public List<String> findEFOTerms(String content) {
    if (content == null || content.isEmpty()) {
      return Collections.emptyList();
    }

    String contentLower = content.toLowerCase();

    // Collect all matches with their spans
    List<TermMatch> allMatches = new ArrayList<>();

    for (String termLower : allTermsLowercase) {
      Pattern pattern = Pattern.compile("\\b" + Pattern.quote(termLower) + "\\b");
      Matcher matcher = pattern.matcher(contentLower);

      while (matcher.find()) {
        allMatches.add(new TermMatch(termLower, matcher.start(), matcher.end()));
      }
    }

    // Sort by length (descending) then by start position
    allMatches.sort(
        Comparator.comparingInt((TermMatch m) -> m.end - m.start)
            .reversed()
            .thenComparingInt(m -> m.start));

    // Select non-overlapping matches (greedy, prefer longer)
    List<TermMatch> selectedMatches = new ArrayList<>();
    for (TermMatch match : allMatches) {
      boolean overlaps = selectedMatches.stream().anyMatch(m -> match.overlaps(m));

      if (!overlaps) {
        selectedMatches.add(match);
      }
    }

    // Map to primary terms and deduplicate
    Set<String> uniqueTerms = new LinkedHashSet<>();
    for (TermMatch match : selectedMatches) {
      String efoId = termToIdCache.get(match.term);
      if (efoId != null) {
        String originalTerm = idToTermCache.get(efoId);
        if (originalTerm != null) {
          uniqueTerms.add(originalTerm);
        }
      } else {
        // Alternative term without primary mapping
        uniqueTerms.add(match.term);
      }
    }

    return new ArrayList<>(uniqueTerms);
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
   * Gets all unique EFO terms.
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

  /** Represents a matched term with its span in the content. */
  private static class TermMatch {
    final String term;
    final int start;
    final int end;

    TermMatch(String term, int start, int end) {
      this.term = term;
      this.start = start;
      this.end = end;
    }

    boolean overlaps(TermMatch other) {
      return this.start < other.end && this.end > other.start;
    }
  }
}
