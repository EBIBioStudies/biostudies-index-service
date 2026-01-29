package uk.ac.ebi.biostudies.index_service.search.query;

import lombok.Value;
import org.apache.lucene.search.Query;

/** Result of query building containing the Lucene query and expansion metadata. */
@Value
public class QueryResult {
  Query query;
  QueryExpansionMetadata expansionMetadata;
  String originalQuery;
}
