package uk.ac.ebi.biostudies.index_service.autocomplete;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;

import org.apache.lucene.index.DirectoryReader;
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
 * <p>Loads all EFO terms and their hierarchical relationships into memory at startup by querying the
 * EFO Lucene index. Matching is token-based and uses a trie built from normalized labels.
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

  /** Fast phrase matcher built from normalized EFO labels. */
  private TokenTrie tokenTrie;

  public EFOTermMatcher(IndexManager indexManager) {
    this.indexManager = Objects.requireNonNull(indexManager, "indexManager must not be null");
  }

  /**
   * Initializes EFO term caches by querying the EFO Lucene index.
   *
   * @throws IllegalStateException if initialization fails
   */
  public void initialize() {
    log.info("Initializing EFO term matcher caches from index...");

    try {
      long startTime = System.currentTimeMillis();

      loadAllEFOTermsFromIndex();
      buildTokenTrie();

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
      Bits liveDocs = MultiBits.getLiveDocs(reader);

      log.debug("Found {} documents in EFO index", maxDoc);

      for (int docId = 0; docId < maxDoc; docId++) {
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

        String[] altTerms = doc.getValues(EFOField.ALTERNATIVE_TERMS.getFieldName());
        if (altTerms != null && altTerms.length > 0) {
          for (String altTerm : altTerms) {
            if (altTerm == null || altTerm.isEmpty()) {
              continue;
            }
            String altTermLower = altTerm.toLowerCase();
            allTermsLowercase.add(altTermLower);
            if (efoId != null) {
              termToIdCache.putIfAbsent(altTermLower, efoId);
            }
            altTermCount++;
          }
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

  private void buildTokenTrie() {
    List<TokenTrie.Entry> entries = new ArrayList<>();

    for (String termLower : allTermsLowercase) {
      String efoId = termToIdCache.get(termLower);
      if (efoId == null) {
        continue;
      }

      String canonicalLabel = idToTermCache.get(efoId);
      if (canonicalLabel == null) {
        continue;
      }

      entries.add(new TokenTrie.Entry(termLower, efoId, canonicalLabel));
    }

    tokenTrie = TokenTrie.build(entries);
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
   * Finds all EFO terms present in the given content string using token-based phrase matching.
   *
   * @param content the text to search for EFO terms
   * @return list of matched EFO terms in original case (empty if content is null/empty)
   */
  public List<String> findEFOTerms(String content) {
    if (content == null || content.isEmpty()) {
      return Collections.emptyList();
    }
    if (tokenTrie == null) {
      throw new IllegalStateException("EFOTermMatcher has not been initialized");
    }

    String[] tokens = TextNormalizer.tokenize(content);
    if (tokens.length == 0) {
      return Collections.emptyList();
    }

    List<TokenTrie.Match> matches = findMatches(tokens);
    if (matches.isEmpty()) {
      return Collections.emptyList();
    }

    // Prefer longer matches first, then earlier occurrences
    matches.sort(
        Comparator.comparingInt((TokenTrie.Match m) -> m.endToken() - m.startToken())
            .reversed()
            .thenComparingInt(TokenTrie.Match::startToken));

    List<TokenTrie.Match> selected = new ArrayList<>();
    for (TokenTrie.Match match : matches) {
      boolean overlaps = false;
      for (TokenTrie.Match existing : selected) {
        if (overlaps(match, existing)) {
          overlaps = true;
          break;
        }
      }
      if (!overlaps) {
        selected.add(match);
      }
    }

    Set<String> uniqueTerms = new LinkedHashSet<>();
    for (TokenTrie.Match match : selected) {
      uniqueTerms.add(match.canonicalLabel());
    }

    return new ArrayList<>(uniqueTerms);
  }

  private List<TokenTrie.Match> findMatches(String[] tokens) {
    List<TokenTrie.Match> matches = new ArrayList<>();

    for (int start = 0; start < tokens.length; start++) {
      TokenTrie.Node node = tokenTrie.root();
      int end = start;

      while (end < tokens.length) {
        node = node.child(tokens[end]);
        if (node == null) {
          break;
        }

        if (node.isTerminal()) {
          matches.add(new TokenTrie.Match(node.efoId(), node.canonicalLabel(), start, end + 1));
        }

        end++;
      }
    }

    return matches;
  }

  private boolean overlaps(TokenTrie.Match a, TokenTrie.Match b) {
    return a.startToken() < b.endToken() && a.endToken() > b.startToken();
  }

  /**
   * Gets the ancestor chain for a given term.
   *
   * @param term the EFO term (case-insensitive)
   * @return list of ancestor terms ordered from root to immediate parent, or empty list if term has
   *     no ancestors or is not found
   */
  public List<String> getAncestors(String term) {
    if (term == null || termToAncestorsCache == null) {
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
    return term != null && termToIdCache != null ? termToIdCache.get(term.toLowerCase()) : null;
  }

  /**
   * Gets the primary term for a given EFO ID.
   *
   * @param efoId the EFO ID (URI)
   * @return the primary term in original case, or null if ID not found
   */
  public String getTerm(String efoId) {
    return efoId != null && idToTermCache != null ? idToTermCache.get(efoId) : null;
  }

  /**
   * Checks if a term exists in the EFO ontology.
   *
   * @param term the term to check (case-insensitive)
   * @return true if term exists as primary or alternative term
   */
  public boolean isEFOTerm(String term) {
    return term != null
        && allTermsLowercase != null
        && allTermsLowercase.contains(term.toLowerCase());
  }

  /**
   * Gets all unique EFO terms.
   *
   * @return unmodifiable set of all lowercase terms
   */
  public Set<String> getAllTerms() {
    if (allTermsLowercase == null) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(allTermsLowercase);
  }

  /**
   * Gets cache statistics for monitoring.
   *
   * @return formatted string with cache sizes
   */
  public String getCacheStats() {
    int terms = allTermsLowercase != null ? allTermsLowercase.size() : 0;
    int withHierarchy = termToAncestorsCache != null ? termToAncestorsCache.size() : 0;
    int nodes = idToTermCache != null ? idToTermCache.size() : 0;
    return String.format("EFOTermMatcher[terms=%d, withHierarchy=%d, nodes=%d]", terms, withHierarchy, nodes);
  }

  private List<String> computeAncestorsWithMemoization(
      String efoId, Map<String, List<String>> nodeParents, Map<String, List<String>> cache) {

    List<String> cached = cache.get(efoId);
    if (cached != null) {
      return cached;
    }

    List<String> parents = nodeParents.get(efoId);
    if (parents == null || parents.isEmpty()) {
      cache.put(efoId, Collections.emptyList());
      return Collections.emptyList();
    }

    String parentId = parents.get(0);
    String parentTerm = idToTermCache.get(parentId);
    if (parentTerm == null) {
      cache.put(efoId, Collections.emptyList());
      return Collections.emptyList();
    }

    List<String> parentAncestors = computeAncestorsWithMemoization(parentId, nodeParents, cache);

    List<String> ancestors = new ArrayList<>(parentAncestors.size() + 1);
    ancestors.addAll(parentAncestors);
    ancestors.add(parentTerm);

    cache.put(efoId, ancestors);
    return ancestors;
  }
}
