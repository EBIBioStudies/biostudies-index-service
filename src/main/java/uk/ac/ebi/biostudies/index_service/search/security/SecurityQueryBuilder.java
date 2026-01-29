package uk.ac.ebi.biostudies.index_service.search.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.analysis.analyzers.AccessFieldAnalyzer;
import uk.ac.ebi.biostudies.index_service.auth.AuthenticationContext;
import uk.ac.ebi.biostudies.index_service.auth.User;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;
import uk.ac.ebi.biostudies.index_service.search.query.QueryBuildException;

/**
 * Applies security constraints to Lucene queries based on user permissions.
 *
 * <p>Filters search results according to:
 *
 * <ul>
 *   <li>User's allow/deny permissions from authentication context
 *   <li>Public access for unauthenticated users
 *   <li>Secret key access when provided
 *   <li>Superuser bypass for unrestricted access
 * </ul>
 */
@Slf4j
@Component
public class SecurityQueryBuilder {
  private final LuceneIndexConfig luceneIndexConfig;

  public SecurityQueryBuilder(LuceneIndexConfig luceneIndexConfig) {
    this.luceneIndexConfig = luceneIndexConfig;
  }

  /**
   * Applies security filtering without a secret key.
   *
   * @param originalQuery the base query to secure
   * @return security-filtered query
   */
  public Query applySecurity(Query originalQuery) {
    return applySecurity(originalQuery, null);
  }

  /**
   * Applies security filtering based on current user permissions and optional secret key.
   *
   * <p>Security logic:
   *
   * <ul>
   *   <li>Superusers: no filtering applied
   *   <li>Authenticated users: apply allow/deny permissions
   *   <li>Unauthenticated users: PUBLIC access only
   *   <li>Secret key: grants additional access when provided
   * </ul>
   *
   * @param originalQuery the base query to secure (may be null)
   * @param seckey optional secret key for additional access
   * @return security-filtered query combining original query with access constraints
   * @throws QueryBuildException if security query parsing fails
   */
  public Query applySecurity(Query originalQuery, String seckey) {
    Query finalQuery;
    QueryParser queryParser =
        new QueryParser(Constants.ACCESS_FIELD, new AccessFieldAnalyzer(luceneIndexConfig));
    queryParser.setSplitOnWhitespace(true);
    BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

    // Get current user from thread-local context
    User currentUser = AuthenticationContext.getCurrentUser();

    // Superuser can access everything
    if (currentUser != null && currentUser.isSuperUser()) {
      log.debug("Superuser access - no security filtering applied");
      return originalQuery;
    }

    // Build ALLOW clause
    StringBuilder securityClause = new StringBuilder();
    if (currentUser != null
        && currentUser.getAllow() != null
        && currentUser.getAllow().length > 0) {
      securityClause.append(String.join(" OR ", currentUser.getAllow()));
      log.debug(
          "User {} has {} allow permissions",
          currentUser.getLogin(),
          currentUser.getAllow().length);
    } else {
      securityClause.append(Constants.PUBLIC);
      log.debug("No authenticated user - applying PUBLIC access only");
    }

    try {
      Query allowQuery = queryParser.parse(securityClause.toString());

      // Handle secret key access
      if (seckey != null && !seckey.isEmpty()) {
        QueryParser secretQueryParser =
            new QueryParser(FieldName.SECRET_KEY.getName(), new KeywordAnalyzer());
        secretQueryParser.setSplitOnWhitespace(true);
        Query secretKeyQuery = secretQueryParser.parse(seckey);

        BooleanQuery booleanQuery =
            new BooleanQuery.Builder()
                .add(secretKeyQuery, BooleanClause.Occur.SHOULD)
                .add(allowQuery, BooleanClause.Occur.SHOULD)
                .build();
        queryBuilder.add(booleanQuery, BooleanClause.Occur.MUST);
        log.debug("Secret key provided - adding to access query");
      } else {
        queryBuilder.add(allowQuery, BooleanClause.Occur.MUST);
      }

      // Build DENY clause
      if (currentUser != null
          && currentUser.getDeny() != null
          && currentUser.getDeny().length > 0) {
        securityClause = new StringBuilder();
        securityClause.append(String.join(" OR ", currentUser.getDeny()));
        Query denyQuery = queryParser.parse(securityClause.toString());
        queryBuilder.add(denyQuery, BooleanClause.Occur.MUST_NOT);
        log.debug(
            "User {} has {} deny restrictions",
            currentUser.getLogin(),
            currentUser.getDeny().length);
      }

      // Combine with original query
      if (originalQuery != null) {
        queryBuilder.add(originalQuery, BooleanClause.Occur.MUST);
      }
      finalQuery = queryBuilder.build();

      log.trace("Final security query: {}", finalQuery);
    } catch (ParseException pex) {
      throw new QueryBuildException("Failed to parse security query", pex);
    }

    return finalQuery;
  }
}
