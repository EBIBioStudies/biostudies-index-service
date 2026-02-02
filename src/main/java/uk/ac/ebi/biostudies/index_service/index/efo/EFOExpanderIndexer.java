package uk.ac.ebi.biostudies.index_service.index.efo;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.config.EFOConfig;

/**
 * Builds query expansion index from EFO ontology for broader search results.
 *
 * <p>Creates expansion mappings: search term → synonyms + child terms. Used by search service to
 * automatically expand user queries with related ontology terms.
 *
 * <p>Example: "cancer" → ["neoplasm", "tumor", "carcinoma", "lung cancer", ...]
 *
 * <p>Index structure:
 *
 * <ul>
 *   <li><b>TERM</b>: Searchable key (primary term + synonyms)
 *   <li><b>EFO</b>: Expansion values (synonyms + children)
 * </ul>
 */
@Slf4j
@Component
public class EFOExpanderIndexer {

  private final EFOConfig efoConfig;

  public EFOExpanderIndexer(EFOConfig efoConfig) {
    this.efoConfig = efoConfig;
  }

  /**
   * Builds expansion index from EFO model. Recursively processes ontology from root, creating
   * expansion documents.
   *
   * @param resolver loaded EFO term resolver
   * @param writer index writer for expansion index
   * @throws IOException if indexing fails
   */
  public void buildIndex(EFOTermResolver resolver, IndexWriter writer) throws IOException {
    log.info("Building EFO expansion index");

    if (resolver == null) {
      log.warn("No EFO resolver—skipping expansion index");
      return;
    }

    EFOModel model = resolver.getModel();
    EFONode root = model.getNodes().get(EFOTermResolver.ROOT_ID);

    int docCount = addNodeAndChildren(root, resolver, writer);

    writer.commit();
    log.info("Expansion index complete: {} documents", docCount);
  }

  /**
   * Recursively indexes node and descendants for query expansion. Returns total documents added.
   */
  private int addNodeAndChildren(EFONode node, EFOTermResolver resolver, IndexWriter writer)
      throws IOException {
    if (node == null) {
      return 0;
    }

    int count = addNodeToIndex(node, resolver, writer) ? 1 : 0;

    for (EFONode child : node.getChildren()) {
      count += addNodeAndChildren(child, resolver, writer);
    }

    return count;
  }

  /**
   * Creates expansion document for single EFO node.
   *
   * <p>Document includes:
   *
   * <ul>
   *   <li>Primary term as searchable key
   *   <li>Alternative terms (synonyms) as keys and expansions
   *   <li>Child terms as expansions (unless organizational class)
   * </ul>
   *
   * @return true if document added, false if skipped
   */
  private boolean addNodeToIndex(EFONode node, EFOTermResolver resolver, IndexWriter writer)
      throws IOException {
    String term = node.getTerm();
    if (term == null || isStopTerm(term)) {
      return false;
    }

    // Collect synonyms (exclude primary term to avoid self-expansion)
    Set<String> synonyms = new HashSet<>(node.getAlternativeTerms());
    synonyms.remove(term);

    // Collect child terms (skip for organizational classes like "experimental factor")
    Set<String> childTerms =
        node.isOrganizationalClass()
            ? new HashSet<>()
            : resolver.getTerms(node.getId(), EFOTermResolver.INCLUDE_CHILDREN);

    // Skip if no meaningful expansion exists
    if (synonyms.isEmpty() && childTerms.isEmpty()) {
      return false;
    }

    Document doc = new Document();
    int termCount = 0;
    int childCount = 0;

    // Add synonyms as both searchable keys and expansion values
    for (String syn : synonyms) {
      if (!isStopExpansionTerm(syn)) {
        addExpansionField(doc, EFOField.QE_TERM.getFieldName(), syn);
        addExpansionField(doc, EFOField.QE_EFO.getFieldName(), syn);
        termCount++;
      }
    }

    // Add children as expansion values only
    for (String child : childTerms) {
      if (!isStopExpansionTerm(child)) {
        addExpansionField(doc, EFOField.QE_EFO.getFieldName(), child);
        childCount++;
      }
    }

    // Add primary term as searchable key
    if (!isStopExpansionTerm(term)) {
      addExpansionField(doc, EFOField.QE_TERM.getFieldName(), term);
      termCount++;
    }

    // Only index if document provides meaningful expansion
    // (multiple search keys OR search key with expansions)
    if (termCount > 1 || (termCount == 1 && childCount > 0)) {
      writer.addDocument(doc);
      return true;
    }

    return false;
  }

  /**
   * Adds expansion field with cleaned/normalized value. Removes special chars except alphanumerics
   * and hyphens.
   */
  private void addExpansionField(Document doc, String fieldName, String value) {
    String cleaned = value.replaceAll("[^\\d\\w-]", " ").toLowerCase();
    doc.add(new StringField(fieldName, cleaned, Field.Store.YES));
  }

  /** Checks if term is in configured stop words list. */
  private boolean isStopTerm(String str) {
    return str == null || efoConfig.getStopWordsSet().contains(str.toLowerCase());
  }

  /**
   * Checks if term should be excluded from expansion index.
   *
   * <p>Filters out:
   *
   * <ul>
   *   <li>Stop words
   *   <li>Very short terms (&lt;3 chars)
   *   <li>Terms with qualifiers: "(NOS)", "[obsolete]", "item1, item2"
   *   <li>Terms with separators: " - ", "/"
   * </ul>
   */
  private boolean isStopExpansionTerm(String str) {
    if (isStopTerm(str) || str.length() < 3) {
      return true;
    }

    // Exclude terms with qualifiers or separators
    return str.matches(".*(\\s\\(.+\\)|\\s\\[.+\\]|,\\s|\\s-\\s|/|NOS).*");
  }
}
