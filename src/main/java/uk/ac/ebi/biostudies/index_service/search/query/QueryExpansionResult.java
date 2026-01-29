package uk.ac.ebi.biostudies.index_service.search.query;

import lombok.Value;
import org.apache.lucene.search.Query;

/** Result of query expansion operation. */
@Value
public class QueryExpansionResult {
  Query expandedQuery;
  QueryExpansionMetadata metadata;

  public static QueryExpansionResult noExpansion(Query originalQuery) {
    return new QueryExpansionResult(originalQuery, QueryExpansionMetadata.empty());
  }
}
