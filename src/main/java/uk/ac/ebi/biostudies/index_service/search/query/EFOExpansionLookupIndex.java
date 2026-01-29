package uk.ac.ebi.biostudies.index_service.search.query;

import java.io.IOException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOIndexFields;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

/**
 * Looks up EFO (Experimental Factor Ontology) expansion terms for query terms.
 *
 * <p>This service searches a separate EFO Lucene index to find synonyms and related ontology terms
 * that can be added to the original query for better recall.
 */
@Slf4j
@Service
public class EFOExpansionLookupIndex {

  /** Maximum number of EFO index hits to retrieve */
  private static final int MAX_INDEX_HITS = 16;

  private final IndexManager indexManager;

  public EFOExpansionLookupIndex(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  /**
   * Retrieves EFO expansion terms (synonyms and ontology terms) for a given query.
   *
   * <p>The method searches the EFO index for matching terms and returns any found synonyms and EFO
   * identifiers. Properly manages index searcher lifecycle.
   *
   * @param originalQuery the original query from the user
   * @return expansion terms containing synonyms and EFO terms (never null)
   */
  public EFOExpansionTerms getExpansionTerms(Query originalQuery) {
    EFOExpansionTerms expansion = new EFOExpansionTerms();
    IndexSearcher searcher = null;

    try {
      // ✅ Acquire searcher from pool
      searcher = indexManager.acquireSearcher(IndexName.EFO);

      // Convert query to search EFO index (changes field to "term")
      Query efoQuery = convertQueryForEfoIndex(originalQuery);

      if (efoQuery == null) {
        log.debug("Could not convert query to EFO format: {}", originalQuery);
        return expansion;
      }

      // Search EFO index
      TopDocs hits = searcher.search(efoQuery, MAX_INDEX_HITS);

      log.debug("Found {} EFO expansion hits for query: {}", hits.scoreDocs.length, originalQuery);

      StoredFields storedFields = searcher.storedFields();
      // Extract expansion terms from hits
      for (ScoreDoc scoreDoc : hits.scoreDocs) {
        Document doc = storedFields.document(scoreDoc.doc);

        // Get synonym terms
        String[] synonyms = doc.getValues(EFOIndexFields.TERM);
        if (synonyms.length > 0) {
          expansion.synonyms.addAll(Arrays.asList(synonyms));
        }

        // Get EFO ontology terms
        String[] efoTerms = doc.getValues(EFOIndexFields.EFO);
        if (efoTerms.length > 0) {
          expansion.efo.addAll(Arrays.asList(efoTerms));
        }
      }

      log.debug(
          "Expansion result: {} synonyms, {} EFO terms",
          expansion.synonyms.size(),
          expansion.efo.size());

    } catch (IOException ex) {
      log.error("IO error while expanding terms for query: {}", originalQuery, ex);
    } catch (Exception ex) {
      log.error("Unexpected error while expanding terms for query: {}", originalQuery, ex);
    } finally {
      // ✅ Always release searcher back to pool
      if (searcher != null) {
        try {
          indexManager.releaseSearcher(IndexName.EFO, searcher);
        } catch (IOException ex) {
          log.error("Error releasing EFO searcher", ex);
        }
      }
    }

    return expansion;
  }

  /**
   * Converts a query to search the EFO index by changing its field to "term".
   *
   * <p>The EFO index uses a standard field name "term" for all lookups, so we need to rewrite the
   * query regardless of what field it originally targeted.
   *
   * @param originalQuery the original query
   * @return a query suitable for searching the EFO index, or null if unsupported
   */
  private Query convertQueryForEfoIndex(Query originalQuery) {
    try {
      if (originalQuery instanceof TermQuery) {
        Term originalTerm = ((TermQuery) originalQuery).getTerm();
        return new TermQuery(new Term(EFOIndexFields.TERM, originalTerm.text()));

      } else if (originalQuery instanceof PhraseQuery) {
        Term[] terms = ((PhraseQuery) originalQuery).getTerms();
        if (terms.length == 0) {
          log.warn("Empty phrase query: {}", originalQuery);
          return null;
        }
        // Convert multi-word phrase to single term for EFO lookup
        StringBuilder text = new StringBuilder();
        for (Term term : terms) {
          if (!text.isEmpty()) {
            text.append(' ');
          }
          text.append(term.text());
        }
        return new TermQuery(new Term(EFOIndexFields.TERM, text.toString()));

      } else if (originalQuery instanceof PrefixQuery) {
        Term originalTerm = ((PrefixQuery) originalQuery).getPrefix();
        return new PrefixQuery(new Term(EFOIndexFields.TERM, originalTerm.text()));

      } else if (originalQuery instanceof WildcardQuery) {
        Term originalTerm = ((WildcardQuery) originalQuery).getTerm();
        return new WildcardQuery(new Term(EFOIndexFields.TERM, originalTerm.text()));

      } else if (originalQuery instanceof FuzzyQuery) {
        Term originalTerm = ((FuzzyQuery) originalQuery).getTerm();
        return new FuzzyQuery(new Term(EFOIndexFields.TERM, originalTerm.text()));

      } else if (originalQuery instanceof TermRangeQuery) {
        TermRangeQuery rangeQuery = (TermRangeQuery) originalQuery;
        return new TermRangeQuery(
            EFOIndexFields.TERM,
            rangeQuery.getLowerTerm(),
            rangeQuery.getUpperTerm(),
            rangeQuery.includesLower(),
            rangeQuery.includesUpper());

      } else {
        log.warn(
            "Unsupported query type for EFO expansion: {}",
            originalQuery.getClass().getSimpleName());
        return null;
      }

    } catch (Exception ex) {
      log.error("Error converting query to EFO format: {}", originalQuery, ex);
      return null;
    }
  }
}
