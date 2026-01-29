package uk.ac.ebi.biostudies.index_service.search.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.analysis.AnalyzerManager;
import uk.ac.ebi.biostudies.index_service.config.SubCollectionConfig;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;
import uk.ac.ebi.biostudies.index_service.search.security.SecurityQueryBuilder;

/**
 * Builds secure, executable Lucene queries from user input.
 *
 * <p>Query Building Pipeline:
 *
 * <ol>
 *   <li>Parse base query string
 *   <li>Expand with EFO terms and synonyms
 *   <li>Apply field-specific filters (MUST clauses)
 *   <li>Apply type exclusion filters (MUST_NOT clauses)
 *   <li>Apply collection hierarchy filters (facet drill-down)
 *   <li>Apply security constraints (user permissions)
 * </ol>
 *
 * <p>Each step is optional and depends on the input parameters and configuration.
 */
@Slf4j
@Service
public class LuceneQueryBuilder {

  private static final String MATCH_ALL_QUERY = "*:*";
  private static final String TYPE_FIELD = "type";
  private static final String PUBLIC_COLLECTION = "public";
  private static final String QUERY_FIELD = "query"; // Skip this in field filters

  private final AnalyzerManager analyzerManager;
  private final IndexManager indexManager;
  private final QueryExpander queryExpander;
  private final SecurityQueryBuilder securityQueryBuilder;
  private final CollectionRegistryService collectionRegistryService;
  private final TaxonomyManager taxonomyManager;
  private final LuceneIndexConfig indexConfig;
  private final SubCollectionConfig subCollectionConfig;
  private final FacetService facetService;

  public LuceneQueryBuilder(
      AnalyzerManager analyzerManager,
      IndexManager indexManager,
      QueryExpander queryExpander,
      SecurityQueryBuilder securityQueryBuilder,
      CollectionRegistryService collectionRegistryService,
      TaxonomyManager taxonomyManager,
      LuceneIndexConfig indexConfig,
      SubCollectionConfig subCollectionConfig,
      FacetService facetService) {
    this.analyzerManager = analyzerManager;
    this.indexManager = indexManager;
    this.queryExpander = queryExpander;
    this.securityQueryBuilder = securityQueryBuilder;
    this.collectionRegistryService = collectionRegistryService;
    this.taxonomyManager = taxonomyManager;
    this.indexConfig = indexConfig;
    this.subCollectionConfig = subCollectionConfig;
    this.facetService = facetService;
  }

  /**
   * Builds a complete, secure query from user input. Always applies security constraints based on
   * the current user context.
   *
   * @param queryString the user's search query (can be empty for match-all)
   * @param collection optional collection filter
   * @param fields optional field-specific filters
   * @return query result containing the Lucene query and expansion metadata
   */
  public QueryResult buildQuery(String queryString, String collection, Map<String, String> fields) {
    log.debug(
        "Building query: query='{}', collection='{}', fields='{}'",
        queryString,
        collection,
        fields);

    try {
      // 1. Parse base query
      Query baseQuery = parseQuery(queryString);

      // 2. Expand query (EFO terms, synonyms)
      QueryExpansionResult expansionResult = queryExpander.expand(baseQuery);
      Query expandedQuery = expansionResult.getExpandedQuery();

      // 3. Apply field filters
      if (fields != null && !fields.isEmpty()) {
        expandedQuery = applyFieldFilters(expandedQuery, fields);
      }

      // 4. Apply type filter (if not already specified)
      if (!containsTypeFilter(queryString, fields)) {
        expandedQuery = applyTypeFilter(expandedQuery);
      }

      // 5. Apply collection filter
      if (needsCollectionFilter(collection)) {
        expandedQuery = applyCollectionFilter(expandedQuery, collection.toLowerCase());
      }

      // 6. Apply security constraints
      Query secureQuery = securityQueryBuilder.applySecurity(expandedQuery);

      log.trace("Final Lucene query: {}", secureQuery);

      return new QueryResult(secureQuery, expansionResult.getMetadata(), queryString);

    } catch (Exception ex) {
      log.error("Error building query: {}", queryString, ex);
      throw new QueryBuildException("Failed to build query: " + queryString, ex);
    }
  }

  /**
   * Builds an unsecured query for system/admin use only. WARNING: Should only be used for internal
   * operations, not user-facing searches.
   *
   * @param queryString the search query
   * @param fields optional field-specific filters
   * @return query result without security constraints
   */
  public QueryResult buildUnsecuredQuery(String queryString, Map<String, String> fields) {
    log.warn("Building UNSECURED query - should only be used for system operations");

    try {
      Query baseQuery = parseQuery(queryString);
      QueryExpansionResult expansionResult = queryExpander.expand(baseQuery);
      Query expandedQuery = expansionResult.getExpandedQuery();

      if (fields != null && !fields.isEmpty()) {
        expandedQuery = applyFieldFilters(expandedQuery, fields);
      }

      return new QueryResult(expandedQuery, expansionResult.getMetadata(), queryString);

    } catch (Exception ex) {
      log.error("Error building unsecured query: {}", queryString, ex);
      throw new QueryBuildException("Failed to build unsecured query: " + queryString, ex);
    }
  }

  /** Parses the query string into a Lucene Query object. */
  private Query parseQuery(String queryString) throws Exception {
    String normalizedQuery = StringUtils.hasText(queryString) ? queryString : MATCH_ALL_QUERY;
    QueryParser parser = createQueryParser();
    return parser.parse(normalizedQuery);
  }

  /** Creates a configured query parser instance. */
  private QueryParser createQueryParser() {
    Analyzer analyzer = analyzerManager.getPerFieldAnalyzerWrapper();
    return new BioStudiesQueryParser(analyzer, indexManager, collectionRegistryService);
  }

  /**
   * Applies field-specific filters to the query. Adds MUST clauses for each selected field/value
   * pair.
   *
   * @param baseQuery the base query to filter
   * @param fields key-value: field name -> field value
   * @return query with field filters applied
   */
  private Query applyFieldFilters(Query baseQuery, Map<String, String> fields) {
    QueryParser parser = createQueryParser();
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(baseQuery, BooleanClause.Occur.MUST);

    List<String> failedFields = new ArrayList<>();

    fields.forEach(
        (fieldName, fieldValue) -> {
          try {

            // Skip special fields
            if (fieldName == null
                || fieldName.isEmpty()
                || QUERY_FIELD.equalsIgnoreCase(fieldName)) {
              return;
            }

            if (fieldValue == null || fieldValue.isEmpty()) {
              return;
            }

            Query fieldQuery = parser.parse(fieldName + ":" + fieldValue);
            builder.add(fieldQuery, BooleanClause.Occur.MUST);

            log.debug("Applied field filter: {}:{}", fieldName, fieldValue);

          } catch (Exception e) {
            log.error("Error applying field filter: {} = {}", fieldName, fieldValue, e);
            failedFields.add(fieldName);
          }
        });

    // Warn if some filters failed
    if (!failedFields.isEmpty()) {
      log.warn("Failed to apply field filters for fields: {}", failedFields);
    }

    return builder.build();
  }

  /**
   * Applies type filter to exclude certain document types. Uses MUST_NOT clause with the configured
   * type filter query.
   *
   * @param originalQuery the query to filter
   * @return query with type filter applied, or original if no filter configured
   */
  private Query applyTypeFilter(Query originalQuery) {
    Query typeFilterQuery = indexConfig.getTypeFilterQuery();

    if (typeFilterQuery == null) {
      log.debug("Type filtering disabled (no excluded types configured)");
      return originalQuery;
    }

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(originalQuery, BooleanClause.Occur.MUST);
    builder.add(typeFilterQuery, BooleanClause.Occur.MUST_NOT);

    log.debug("Applied type filter to exclude: {}", typeFilterQuery);
    return builder.build();
  }

  /**
   * Applies collection filter using taxonomy facet drill-down. Includes sub-collections if they
   * exist.
   *
   * @param query the base query
   * @param collectionName the collection name to filter by
   * @return query with collection filter applied
   */
  private Query applyCollectionFilter(Query query, String collectionName) {

    // Build collection list (include sub-collections)
    List<String> collections = buildCollectionList(collectionName);

    PropertyDescriptor facetDescriptor =
        collectionRegistryService.getPropertyDescriptor(FieldName.FACET_COLLECTION.getName());

    if (facetDescriptor == null) {
      log.warn("Facet collection descriptor not found");
      return query;
    }

    var facetsConfig = taxonomyManager.getFacetsConfig();
    if (facetsConfig == null) {
      log.warn("Facets configuration not available, skipping collection filter");
      return query;
    }
    Map<PropertyDescriptor, List<String>> selectedFacetValues = new HashMap<>();
    selectedFacetValues.put(facetDescriptor, collections);
    Query filteredQuery =
        facetService.addFacetDrillDownFilters(facetsConfig, query, selectedFacetValues);

    log.debug(
        "Applied collection filter: {} (with {} sub-collections)",
        collectionName,
        collections.size() - 1);

    return filteredQuery;
  }

  /** Builds a list of collections including the specified collection and its subcollections. */
  private List<String> buildCollectionList(String collectionName) {
    List<String> collections = new ArrayList<>();
    collections.add(collectionName);

    List<String> subcollections = subCollectionConfig.getChildren(collectionName);
    if (!subcollections.isEmpty()) {
      collections.addAll(subcollections);
      log.debug("Including {} subcollections for '{}'", subcollections.size(), collectionName);
    }

    return collections;
  }

  /** Checks if the query already contains a type filter. */
  private boolean containsTypeFilter(String queryString, Map<String, String> fields) {
    boolean inQueryString =
        queryString != null && queryString.toLowerCase().contains(TYPE_FIELD + ":");
    boolean inFields = fields != null && fields.containsKey(TYPE_FIELD);
    return inQueryString || inFields;
  }

  /** Checks if a collection filter should be applied. */
  private boolean needsCollectionFilter(String collection) {
    return StringUtils.hasText(collection) && !PUBLIC_COLLECTION.equalsIgnoreCase(collection);
  }
}
