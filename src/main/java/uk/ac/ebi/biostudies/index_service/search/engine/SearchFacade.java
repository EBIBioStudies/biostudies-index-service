package uk.ac.ebi.biostudies.index_service.search.engine;

import java.util.List;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import uk.ac.ebi.biostudies.index_service.exceptions.SearchException;

/**
 * Facade interface for executing domain-specific searches that return typed results.
 *
 * <p>Implementations wrap a generic query executor and transform raw Lucene documents into
 * domain-specific DTOs, providing type-safe search operations with optional pagination.
 *
 * @param <T> the type of domain object returned in search results
 */
public interface SearchFacade<T> {

  /**
   * Executes a paginated or non-paginated search based on the criteria.
   *
   * <p>If criteria includes pagination ({@link SearchCriteria#isPaginated()} returns true), returns
   * paginated results. Otherwise, returns all matching results up to the configured limit (either
   * from criteria or executor default).
   *
   * @param criteria the {@link SearchCriteria} including query, pagination, sorting, and limit
   * @return paginated search results containing domain objects of type T
   * @throws SearchException if the search fails
   * @throws IllegalArgumentException if criteria is null
   */
  PaginatedResult<T> search(SearchCriteria criteria);

  /**
   * Executes a non-paginated search and returns all matching results as a simple list.
   *
   * <p>This is a convenience method equivalent to calling {@link #search(SearchCriteria)} with
   * non-paginated criteria and extracting the results list.
   *
   * <p>Results are limited to the executor's default maximum (typically 10,000 documents).
   *
   * <p><b>Warning:</b> Use only when the expected result set is small (typically &lt; 1,000
   * documents). For large result sets, use {@link #search(SearchCriteria)} with pagination.
   *
   * @param query the Lucene query to execute
   * @return a list of matching domain objects, limited to the default maximum
   * @throws SearchException if the search fails
   * @throws IllegalArgumentException if query is null
   */
  default List<T> searchAll(Query query) {
    SearchCriteria criteria = SearchCriteria.of(query);
    return search(criteria).results();
  }

  /**
   * Executes a non-paginated search with a custom result limit and returns matching results as a
   * simple list.
   *
   * <p>This is useful when you need all results but want to set a safety limit lower than the
   * executor's default maximum.
   *
   * @param query the Lucene query to execute
   * @param limit the maximum number of results to return, must be > 0
   * @return a list of matching domain objects, limited to the specified maximum
   * @throws SearchException if the search fails
   * @throws IllegalArgumentException if query is null or limit <= 0
   */
  default List<T> searchAll(Query query, int limit) {
    SearchCriteria criteria = new SearchCriteria.Builder(query).limit(limit).build();
    return search(criteria).results();
  }

  /**
   * Executes a non-paginated search with sorting and returns all matching results as a simple list.
   *
   * <p>Results are limited to the executor's default maximum (typically 10,000 documents).
   *
   * @param query the Lucene query to execute
   * @param sort the sort criteria
   * @return a list of matching domain objects, limited to the default maximum
   * @throws SearchException if the search fails
   * @throws IllegalArgumentException if query is null
   */
  default List<T> searchAll(Query query, Sort sort) {
    SearchCriteria criteria = new SearchCriteria.Builder(query).sort(sort).build();
    return search(criteria).results();
  }

  /**
   * Executes a non-paginated search with sorting and a custom result limit, returning matching
   * results as a simple list.
   *
   * @param query the Lucene query to execute
   * @param sort the sort criteria
   * @param limit the maximum number of results to return, must be > 0
   * @return a list of matching domain objects, limited to the specified maximum
   * @throws SearchException if the search fails
   * @throws IllegalArgumentException if query is null or limit <= 0
   */
  default List<T> searchAll(Query query, Sort sort, int limit) {
    SearchCriteria criteria = new SearchCriteria.Builder(query).sort(sort).limit(limit).build();
    return search(criteria).results();
  }
}
