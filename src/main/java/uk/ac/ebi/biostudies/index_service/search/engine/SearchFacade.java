package uk.ac.ebi.biostudies.index_service.search.engine;

/**
 * Facade interface for executing domain-specific searches that return typed results.
 *
 * <p>Implementations wrap a generic query executor and transform raw Lucene documents into
 * domain-specific DTOs, providing type-safe search operations with pagination support.
 *
 * @param <T> the type of domain object returned in search results
 */
public interface SearchFacade<T> {

  /**
   * Executes a search using the provided Lucene query and returns paginated results.
   *
   * @param criteria the {@link SearchCriteria} including query, pagination, and sorting criteria
   * @return paginated search results containing domain objects of type T
   * @throws IllegalArgumentException if query is null
   */
  PaginatedResult<T> search(SearchCriteria criteria);
}
