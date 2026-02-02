package uk.ac.ebi.biostudies.index_service.search.searchers;

import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.exceptions.SearchException;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.search.engine.LuceneQueryExecutor;
import uk.ac.ebi.biostudies.index_service.search.engine.PaginatedResult;
import uk.ac.ebi.biostudies.index_service.search.engine.SearchCriteria;
import uk.ac.ebi.biostudies.index_service.search.engine.SearchFacade;
import uk.ac.ebi.biostudies.index_service.search.engine.SubmissionSearchHit;

/**
 * Search facade for executing submission searches and mapping results to {@link
 * SubmissionSearchHit}.
 *
 * <p>This facade wraps {@link LuceneQueryExecutor} and handles the transformation of raw Lucene
 * documents into domain-specific submission DTOs. All exceptions are wrapped in {@link
 * SearchException}.
 */
@Slf4j
@Component
public class SubmissionSearcher implements SearchFacade<SubmissionSearchHit> {

  private final LuceneQueryExecutor queryExecutor;
  private final DocumentMapper<SubmissionSearchHit> mapper;

  /**
   * Constructs a submission searcher.
   *
   * @param queryExecutor the Lucene query executor
   * @param mapper the document-to-DTO mapper
   * @throws NullPointerException if any parameter is null
   */
  public SubmissionSearcher(
      LuceneQueryExecutor queryExecutor, DocumentMapper<SubmissionSearchHit> mapper) {
    this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor must not be null");
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  /**
   * Executes a submission search using the provided criteria.
   *
   * @param criteria the search criteria including query, pagination, and sorting
   * @return paginated search results containing submission search hits
   * @throws SearchException if the search fails due to I/O errors, invalid criteria, or mapping
   *     failures
   * @throws NullPointerException if criteria is null
   */
  @Override
  public PaginatedResult<SubmissionSearchHit> search(SearchCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria must not be null");

    try {
      log.debug("Executing submission search with criteria: {}", criteria);

      PaginatedResult<Document> documents = queryExecutor.execute(IndexName.SUBMISSION, criteria);
      PaginatedResult<SubmissionSearchHit> results = documents.map(mapper::toDto);

      log.debug(
          "Submission search completed: {} results, {} total hits",
          results.results().size(),
          results.totalHits());

      return results;

    } catch (IOException e) {
      log.error("I/O error during submission search: {}", criteria, e);
      throw new SearchException("Failed to execute submission search", e);

    } catch (IllegalArgumentException e) {
      log.error("Invalid search criteria: {}", criteria, e);
      throw new SearchException("Invalid search criteria: " + e.getMessage(), e);

    } catch (IllegalStateException e) {
      log.error("Document mapping error during submission search: {}", criteria, e);
      throw new SearchException("Failed to map search results: " + e.getMessage(), e);

    } catch (Exception e) {
      log.error("Unexpected error during submission search: {}", criteria, e);
      throw new SearchException("Unexpected search error", e);
    }
  }
}
