package uk.ac.ebi.biostudies.index_service.search.searchers;

import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.exceptions.SearchException;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.search.engine.DocumentMapper;
import uk.ac.ebi.biostudies.index_service.search.engine.LuceneQueryExecutor;
import uk.ac.ebi.biostudies.index_service.search.engine.PaginatedResult;
import uk.ac.ebi.biostudies.index_service.search.engine.SearchCriteria;
import uk.ac.ebi.biostudies.index_service.search.engine.SearchFacade;

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
public class EFOSearcher implements SearchFacade<EFOSearchHit> {

  private final LuceneQueryExecutor queryExecutor;
  private final DocumentMapper<EFOSearchHit> mapper;

  /**
   * Constructs a submission searcher.
   *
   * @param queryExecutor the Lucene query executor
   * @param mapper the document-to-DTO mapper
   * @throws NullPointerException if any parameter is null
   */
  public EFOSearcher(LuceneQueryExecutor queryExecutor, DocumentMapper<EFOSearchHit> mapper) {
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
  public PaginatedResult<EFOSearchHit> search(SearchCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria must not be null");

    try {
      log.debug("Executing submission search with criteria: {}", criteria);

      PaginatedResult<Document> documents = queryExecutor.execute(IndexName.EFO, criteria);
      PaginatedResult<EFOSearchHit> results = documents.map(mapper::toDto);

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

  /**
   * Gets the document frequency for a specific term in the submission index.
   *
   * <p>Document frequency is the number of documents that contain the term in the specified field.
   *
   * <p><strong>Performance:</strong> This is a fast O(1) operation using Lucene's term dictionary
   * (inverted index lookup). It does not scan documents.
   *
   * @param term the term text to check (will be lowercased for matching)
   * @return the number of documents containing the term, or 0 if term not found or invalid inputs
   * @throws IOException if there's an error accessing the index
   */
  public int getTermFrequency(String term) throws IOException {
    return queryExecutor.getTermFrequency(Constants.CONTENT, term, IndexName.SUBMISSION);
  }
}
