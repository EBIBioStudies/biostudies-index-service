package uk.ac.ebi.biostudies.index_service.search.query;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.apache.lucene.search.Query;

/** Result of query building containing the Lucene query and expansion metadata. */
@Data
@Builder
public class QueryResult {

  /** The built Lucene query ready for execution. */
  private final Query query;

  /** List of EFO terms that were expanded into the query. */
  @Builder.Default
  private final Set<String> expandedEfoTerms = Collections.emptySet();

  /** List of synonyms that were expanded into the query. */
  @Builder.Default
  private final Set<String> expandedSynonyms = Collections.emptySet();

  /**
   * Creates a query result without expansion.
   *
   * @param query the Lucene query
   * @return query result with empty expansion lists
   */
  public static QueryResult withoutExpansion(Query query) {
    return QueryResult.builder()
        .query(query)
        .expandedEfoTerms(Collections.emptySet())
        .expandedSynonyms(Collections.emptySet())
        .build();
  }
}