package uk.ac.ebi.biostudies.index_service.search.query;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import uk.ac.ebi.biostudies.index_service.analysis.AnalyzerManager;

/**
 * Expands queries with ontology terms (EFO) and synonyms.
 * Recursively processes Boolean queries and expands expandable fields.
 */
@Slf4j
@Component
public class QueryExpander {

  private static final int MAX_EXPANSION_TERMS = 100;

  private final EFOExpansionLookupIndex efoExpansionLookupIndex;
  private final AnalyzerManager analyzerManager;

  public QueryExpander(
      EFOExpansionLookupIndex efoExpansionLookupIndex,
      AnalyzerManager analyzerManager) {
    this.efoExpansionLookupIndex = efoExpansionLookupIndex;
    this.analyzerManager = analyzerManager;
  }

  /**
   * Expands a query with EFO terms and synonyms.
   *
   * @param baseQuery the original query
   * @return expansion result containing expanded query and metadata
   */
  public QueryResult expand(Query baseQuery) {
    log.debug("Expanding query: {}", baseQuery);

    try {
      Set<String> expandableFields = analyzerManager.getExpandableFieldNames();

      // Perform expansion
      QueryExpansionPair result = expandInternal(expandableFields, baseQuery);

      if (result.expansionTerms == null) {
        return QueryResult.withoutExpansion(baseQuery);
      }

      // Build metadata
      Set<String> efoTerms = result.expansionTerms.efo;
      Set<String> synonyms = result.expansionTerms.synonyms;
      boolean tooMany = (efoTerms.size() + synonyms.size()) > MAX_EXPANSION_TERMS;

      return QueryResult.builder()
          .query(result.query)
          .expandedEfoTerms(efoTerms)
          .expandedSynonyms(synonyms)
          .build();

    } catch (Exception ex) {
      log.error("Error expanding query", ex);
      return QueryResult.withoutExpansion(baseQuery);
    }
  }

  /**
   * Internal recursive expansion logic.
   */
  private QueryExpansionPair expandInternal(
      Set<String> expandableFields,
      Query query) throws IOException {

    // Don't expand MatchAllDocsQuery
    if (query.equals(new MatchAllDocsQuery())) {
      return new QueryExpansionPair(query, null);
    }

    // Recursively expand BooleanQuery
    if (query instanceof BooleanQuery) {
      return expandBooleanQuery(expandableFields, (BooleanQuery) query);
    }

    // Don't expand prefix or wildcard queries (side-effects)
    if (query instanceof PrefixQuery || query instanceof WildcardQuery) {
      return new QueryExpansionPair(query, null);
    }

    // Expand single term/phrase queries
    return doExpand(expandableFields, query);
  }

  /**
   * Expands a BooleanQuery by recursively expanding each clause.
   */
  private QueryExpansionPair expandBooleanQuery(
      Set<String> expandableFields,
      BooleanQuery boolQuery) throws IOException {

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    EFOExpansionTerms combinedTerms = new EFOExpansionTerms();

    for (BooleanClause clause : boolQuery.clauses()) {
      QueryExpansionPair expanded = expandInternal(expandableFields, clause.query());
      builder.add(expanded.query, clause.occur());

      // Merge expansion terms
      if (expanded.expansionTerms != null) {
        if (combinedTerms.term == null) {
          combinedTerms.term = expanded.expansionTerms.term;
        } else {
          combinedTerms.term = combinedTerms.term + " " + expanded.expansionTerms.term;
        }
        combinedTerms.efo.addAll(expanded.expansionTerms.efo);
        combinedTerms.synonyms.addAll(expanded.expansionTerms.synonyms);
      }
    }

    return new QueryExpansionPair(builder.build(), combinedTerms);
  }

  /**
   * Performs the actual expansion for a single query term.
   */
  private QueryExpansionPair doExpand(
      Set<String> expandableFields,
      Query query) throws IOException {

    String field = getQueryField(query);

    // Skip if field is not expandable
    if (field == null || !expandableFields.contains(field)) {
      return new QueryExpansionPair(query, null);
    }

    // Get EFO expansion terms from lookup index
    EFOExpansionTerms expansionTerms = efoExpansionLookupIndex.getExpansionTerms(query);

    // Check if too many terms
    if (expansionTerms.efo.size() + expansionTerms.synonyms.size() > MAX_EXPANSION_TERMS) {
      log.warn("Too many expansion terms for {}", query);
      return new QueryExpansionPair(query, expansionTerms);
    }

    // Build expanded query if we have expansion terms
    if (!expansionTerms.efo.isEmpty() || !expansionTerms.synonyms.isEmpty()) {
      BooleanQuery.Builder boolQueryBuilder = new BooleanQuery.Builder();
      boolQueryBuilder.add(query, BooleanClause.Occur.SHOULD);

      // Add synonym terms
      for (String term : expansionTerms.synonyms) {
        Query synonymPart = newQueryFromString(term.trim(), field);
        if (!queryPartIsRedundant(query, synonymPart)) {
          boolQueryBuilder.add(synonymPart, BooleanClause.Occur.SHOULD);
        }
      }

      // Add EFO terms
      for (String term : expansionTerms.efo) {
        Query expansionPart = newQueryFromString(term.trim(), field);
        boolQueryBuilder.add(expansionPart, BooleanClause.Occur.SHOULD);
      }

      return new QueryExpansionPair(boolQueryBuilder.build(), expansionTerms);
    }

    return new QueryExpansionPair(query, null);
  }

  /**
   * Extracts the field name from various query types.
   */
  private String getQueryField(Query query) {
    try {
      if (query instanceof PrefixQuery) {
        return ((PrefixQuery) query).getPrefix().field();
      } else if (query instanceof WildcardQuery) {
        return ((WildcardQuery) query).getTerm().field();
      } else if (query instanceof TermRangeQuery) {
        return ((TermRangeQuery) query).getField();
      } else if (query instanceof FuzzyQuery) {
        return ((FuzzyQuery) query).getTerm().field();
      } else if (query instanceof TermQuery) {
        return ((TermQuery) query).getTerm().field();
      } else if (query instanceof PhraseQuery) {
        Term[] terms = ((PhraseQuery) query).getTerms();
        if (terms.length == 0) {
          log.error("No terms found for query [{}]", query);
          return null;
        }
        return terms[0].field();
      } else {
        log.error("Unsupported class [{}] for query [{}]",
            query.getClass().getName(), query);
        return null;
      }
    } catch (UnsupportedOperationException ex) {
      log.error("Query of [{}], class [{}] doesn't allow us to get its terms extracted",
          query, query.getClass().getCanonicalName());
      return null;
    }
  }

  /**
   * Creates a query from a text string (TermQuery or PhraseQuery).
   */
  private Query newQueryFromString(String text, String field) {
    if (text.contains(" ")) {
      String[] tokens = text.split("\\s+");
      PhraseQuery.Builder builder = new PhraseQuery.Builder();
      for (String token : tokens) {
        builder.add(new Term(field, token));
      }
      return builder.build();
    } else {
      return new TermQuery(new Term(field, text));
    }
  }

  /**
   * Checks if a query part is redundant (already covered by prefix/wildcard).
   */
  private boolean queryPartIsRedundant(Query query, Query part) {
    Term partTerm = getFirstTerm(part);

    if (query instanceof PrefixQuery) {
      Term prefixTerm = ((PrefixQuery) query).getPrefix();
      return prefixTerm.field().equals(partTerm.field())
          && partTerm.text().startsWith(prefixTerm.text());
    } else if (query instanceof WildcardQuery) {
      Term wildcardTerm = ((WildcardQuery) query).getTerm();
      String wildcard = "^" + wildcardTerm.text()
          .replaceAll("\\?", "\\.")
          .replaceAll("\\*", "\\.*") + "$";
      return wildcardTerm.field().equals(partTerm.field())
          && partTerm.text().matches(wildcard);
    } else {
      return query.toString().equals(part.toString());
    }
  }

  /**
   * Extracts the first term from a query.
   */
  private Term getFirstTerm(Query query) {
    if (query instanceof BooleanQuery) {
      List<BooleanClause> clauses = ((BooleanQuery) query).clauses();
      if (!clauses.isEmpty()) {
        return getFirstTerm(clauses.get(0).query());
      }
      return new Term("", "");
    } else if (query instanceof PrefixQuery) {
      return ((PrefixQuery) query).getPrefix();
    } else if (query instanceof WildcardQuery) {
      return ((WildcardQuery) query).getTerm();
    } else if (query instanceof TermRangeQuery) {
      return new Term(((TermRangeQuery) query).getField(), "");
    } else if (query instanceof FuzzyQuery) {
      return ((FuzzyQuery) query).getTerm();
    } else if (query instanceof TermQuery) {
      return ((TermQuery) query).getTerm();
    } else if (query instanceof PhraseQuery) {
      Term[] terms = ((PhraseQuery) query).getTerms();
      if (terms.length == 0) {
        log.error("No terms found for query [{}]", query);
        return new Term("", "");
      }
      return terms[0];
    } else {
      log.error("Unsupported class [{}] for query [{}]",
          query.getClass().getName(), query);
      return new Term("", "");
    }
  }

  /**
   * Internal helper class for expansion results.
   */
  private static class QueryExpansionPair {
    final Query query;
    final EFOExpansionTerms expansionTerms;

    QueryExpansionPair(Query query, EFOExpansionTerms expansionTerms) {
      this.query = query;
      this.expansionTerms = expansionTerms;
    }
  }
}
