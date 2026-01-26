package uk.ac.ebi.biostudies.index_service.index.efo;

import static uk.ac.ebi.biostudies.index_service.registry.model.LuceneFieldTypes.NOT_ANALYZED_STORED;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Indexes EFO ontology into Lucene for term expansion and synonym search.
 *
 * <p>Creates two types of documents:
 *
 * <ul>
 *   <li><b>Node documents</b>: Full EFO metadata (ID, term, parents, children)
 *   <li><b>Alternative term documents</b>: Standalone searchable synonyms for query expansion
 * </ul>
 *
 * <p>Augments ontology with frequent terms from main submission index (≥10 doc frequency).
 */
@Slf4j
@Component
public class EFOIndexer {
  private static final int MIN_DOC_FREQ = 10;
  private static final int MIN_TERM_LENGTH = 4;

  private final IndexManager indexManager;
  private final EFOExpanderIndexer expanderIndexer;

  public EFOIndexer(IndexManager indexManager, EFOExpanderIndexer expanderIndexer) {
    this.indexManager = indexManager;
    this.expanderIndexer = expanderIndexer;
  }

  /**
   * Rebuilds EFO index from ontology model.
   *
   * <p>Process:
   *
   * <ol>
   *   <li>Deletes existing index content
   *   <li>Recursively indexes all nodes from EFO root
   *   <li>Adds frequent terms from submission index
   *   <li>Commits and refreshes searchers
   * </ol>
   *
   * @param resolver loaded EFO term resolver with ontology model
   * @throws IOException if indexing or commit fails
   */
  public void indexEFO(EFOTermResolver resolver) throws IOException {
    log.info("Starting EFO indexing");

    if (resolver == null) {
      log.warn("No EFO resolver—skipping indexing");
      return;
    }

    EFOModel model = resolver.getModel();
    IndexWriter writer = indexManager.getEFOIndexWriter();

    log.info("Deleting existing EFO index content");
    writer.deleteAll();

    // Track indexed terms to avoid duplicates
    Set<String> indexedTerms = new HashSet<>();

    // Index ontology from root
    EFONode root = model.getNodes().get(EFOTermResolver.ROOT_ID);
    addNodeAndChildren(root, writer, indexedTerms);

    // Augment with frequent submission terms
    addMainIndexTerms(writer, indexedTerms);

    // Build expansion index
    expanderIndexer.buildIndex(resolver, writer);

    // Commit and make searchable
    writer.commit();
    indexManager.refreshAll();

    log.info("EFO indexing complete: {} unique terms indexed", indexedTerms.size());
  }

  /**
   * Recursively indexes node and all descendants. Performs depth-first traversal of ontology
   * hierarchy.
   *
   * @param node current EFO node to index
   * @param writer EFO index writer
   * @param indexedTerms set tracking already-indexed term names (lowercase)
   */
  private void addNodeAndChildren(EFONode node, IndexWriter writer, Set<String> indexedTerms) {
    if (node != null) {
      addNodeToIndex(node, writer, indexedTerms);
      for (EFONode child : node.getChildren()) {
        addNodeAndChildren(child, writer, indexedTerms);
      }
    }
  }

  /**
   * Indexes single EFO node with full metadata. Skips if term already indexed (avoids duplicate
   * primary terms). Creates separate documents for alternative terms (query expansion pattern).
   *
   * @param node EFO node to index
   * @param writer EFO index writer
   * @param indexedTerms set tracking indexed terms (lowercase)
   */
  private void addNodeToIndex(EFONode node, IndexWriter writer, Set<String> indexedTerms) {
    String term = node.getTerm();
    if (term == null || indexedTerms.contains(term.toLowerCase())) {
      return;
    }
    indexedTerms.add(term.toLowerCase());

    Document doc = new Document();

    // ID and EFO accession (searchable, stored, not sortable)
    doc.add(new Field(EFOField.ID.getFieldName(), node.getId().toLowerCase(), NOT_ANALYZED_STORED));

    if (node.getEfoUri() != null) {
      doc.add(
          new Field(
              EFOField.EFO_ID.getFieldName(), node.getEfoUri().toLowerCase(), NOT_ANALYZED_STORED));
    }

    // Primary term (searchable, sortable, stored)
    String termLower = term.toLowerCase();
    doc.add(new StringField(EFOField.TERM.getFieldName(), termLower, Field.Store.NO));
    doc.add(new SortedDocValuesField(EFOField.TERM.getFieldName(), new BytesRef(termLower)));
    doc.add(new StoredField(EFOField.TERM.getFieldName(), term));

    // Alternative terms as separate documents (query expansion pattern)
    if (!node.getAlternativeTerms().isEmpty()) {
      createDocPerAlternativeTerm(writer, node.getAlternativeTerms());
    }

    // Parent node IDs (multi-valued)
    for (EFONode parent : node.getParents()) {
      if (parent.getId() != null) {
        doc.add(
            new Field(
                EFOField.PARENT.getFieldName(), parent.getId().toLowerCase(), NOT_ANALYZED_STORED));
      }
    }

    // Children node IDs (multi-valued)
    for (EFONode child : node.getChildren()) {
      if (child.getId() != null) {
        doc.add(
            new Field(
                EFOField.CHILDREN.getFieldName(),
                child.getId().toLowerCase(),
                NOT_ANALYZED_STORED));
      }
    }

    try {
      writer.addDocument(doc);
    } catch (IOException e) {
      log.error("Failed to index EFO node: {}", node.getId(), e);
    }
  }

  /**
   * Creates separate document per alternative term for independent query matching.
   *
   * <p>Pattern: Each synonym = standalone searchable entry without full node metadata. Enables
   * query expansion where search for synonym returns multiple EFO matches.
   *
   * @param writer EFO index writer
   * @param altTerms set of alternative term strings
   */
  private void createDocPerAlternativeTerm(IndexWriter writer, Set<String> altTerms) {
    for (String term : altTerms) {
      if (term != null) {
        try {
          Document doc = new Document();
          String termLower = term.toLowerCase();

          // Searchable, sortable, stored (same as primary term pattern)
          doc.add(
              new StringField(
                  EFOField.ALTERNATIVE_TERMS.getFieldName(), termLower, Field.Store.NO));
          doc.add(
              new SortedDocValuesField(
                  EFOField.ALTERNATIVE_TERMS.getFieldName(), new BytesRef(termLower)));
          doc.add(new StoredField(EFOField.ALTERNATIVE_TERMS.getFieldName(), term));

          writer.addDocument(doc);
        } catch (IOException e) {
          log.debug("Failed to index alternative term: {}", term, e);
        }
      }
    }
  }

  /**
   * Extracts frequent terms from submission index, adds as EFO alternative terms.
   *
   * <p>Criteria:
   *
   * <ul>
   *   <li>Document frequency ≥ 10
   *   <li>Term length ≥ 4 characters
   *   <li>Not already indexed
   * </ul>
   *
   * <p>Uses thread-safe searcher acquisition (acquire/release pattern).
   *
   * @param efoWriter EFO index writer
   * @param existingTerms set of already-indexed terms (lowercase) to avoid duplicates
   * @throws IOException if index access or document addition fails
   */
  private void addMainIndexTerms(IndexWriter efoWriter, Set<String> existingTerms)
      throws IOException {

    // Acquire searcher (thread-safe, ref-counted)
    IndexSearcher searcher = indexManager.acquireSearcher(IndexName.SUBMISSION);
    try {
      IndexReader reader = searcher.getIndexReader();

      // Extract terms from CONTENT field
      Terms terms = MultiTerms.getTerms(reader, Constants.CONTENT);
      if (terms == null) {
        log.warn("No terms found in {} field—skipping augmentation", Constants.CONTENT);
        return;
      }

      TermsEnum termsEnum = terms.iterator();
      BytesRef termBytes;
      int addedCount = 0;

      while ((termBytes = termsEnum.next()) != null) {
        String term = termBytes.utf8ToString();
        String termLower = term.toLowerCase();

        // Filter: length, duplicates, frequency
        if (term.length() < MIN_TERM_LENGTH || existingTerms.contains(termLower)) {
          continue;
        }

        if (termsEnum.docFreq() >= MIN_DOC_FREQ) {
          existingTerms.add(termLower);

          // Create standalone alternative term document
          Document doc = new Document();
          doc.add(
              new StringField(
                  EFOField.ALTERNATIVE_TERMS.getFieldName(), termLower, Field.Store.NO));
          doc.add(
              new SortedDocValuesField(
                  EFOField.ALTERNATIVE_TERMS.getFieldName(), new BytesRef(termLower)));
          doc.add(new StoredField(EFOField.ALTERNATIVE_TERMS.getFieldName(), term));

          efoWriter.addDocument(doc);
          addedCount++;
        }
      }

      log.info("Added {} frequent submission terms to EFO index", addedCount);

    } finally {
      // Always release searcher (required for ref-counting)
      indexManager.releaseSearcher(IndexName.SUBMISSION, searcher);
    }
  }
}
