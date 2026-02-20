package uk.ac.ebi.biostudies.index_service.submission;

import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.exceptions.SubmissionNotAccessibleException;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.model.IndexedSubmission;
import uk.ac.ebi.biostudies.index_service.registry.model.SubmissionField;
import uk.ac.ebi.biostudies.index_service.search.engine.DocumentMapper;
import uk.ac.ebi.biostudies.index_service.search.engine.LuceneQueryExecutor;
import uk.ac.ebi.biostudies.index_service.search.engine.SearchCriteria;
import uk.ac.ebi.biostudies.index_service.search.security.SecurityQueryBuilder;

/**
 * Service for retrieving individual submissions by accession from the index.
 *
 * <p>Focuses on exact-match lookups with security filtering, unlike general search services.
 */
@Slf4j
@Service
public class SubmissionRetriever {

  private final LuceneQueryExecutor luceneQueryExecutor;
  private final SecurityQueryBuilder securityQueryBuilder;
  private final DocumentMapper<IndexedSubmission> mapper;

  public SubmissionRetriever(
      LuceneQueryExecutor luceneQueryExecutor,
      SecurityQueryBuilder securityQueryBuilder,
      DocumentMapper<IndexedSubmission> mapper) {
    this.luceneQueryExecutor = luceneQueryExecutor;
    this.securityQueryBuilder = securityQueryBuilder;
    this.mapper = mapper;
  }

  /**
   * Retrieves a submission by accession and validates its type.
   *
   * @param accession accession number (e.g. S-BSST1432)
   * @param secretKey optional secret for unreleased submissions
   * @param type expected submission type (case-insensitive, trimmed)
   * @return the submission if found, accessible, and matching type; empty otherwise
   * @throws SubmissionNotAccessibleException if submission exists but user lacks access
   */
  public Optional<IndexedSubmission> getSubmissionByAccessionAndType(
      String accession, String secretKey, String type) throws SubmissionNotAccessibleException {
    Optional<IndexedSubmission> optionalIndexedSubmission =
        getSubmissionByAccession(accession, secretKey);
    if (optionalIndexedSubmission.isEmpty()) {
      return Optional.empty();
    }
    IndexedSubmission indexedSubmission = optionalIndexedSubmission.get();
    if (!indexedSubmission.getType().equalsIgnoreCase(type.trim())) {
      return Optional.empty();
    }
    return optionalIndexedSubmission;
  }

  /**
   * Retrieves a submission by accession with security checks.
   *
   * @param accession accession number (e.g. S-BSST1432)
   * @param secretKey optional secret for unreleased submissions
   * @return the submission if found and accessible; empty if not found
   * @throws SubmissionNotAccessibleException if submission exists but user lacks access
   */
  public Optional<IndexedSubmission> getSubmissionByAccession(String accession, String secretKey)
      throws SubmissionNotAccessibleException {
    Optional<Document> documentOpt = getSubmissionDocumentByAccession(accession, secretKey);
    if (documentOpt.isEmpty()) {
      if (isDocumentPresent(accession)) {
        // The document exists, it's just not accessible for the current user
        throw new SubmissionNotAccessibleException();
      } else {
        return Optional.empty();
      }
    }
    IndexedSubmission indexedSubmission = mapper.toDto(documentOpt.get());
    return indexedSubmission == null ? Optional.empty() : Optional.of(indexedSubmission);
  }

  private Optional<Document> getSubmissionDocumentByAccession(String accession, String secretKey) {
    try {
      Query basicQuery = buildQueryWithoutSecurity(accession);
      Query query = securityQueryBuilder.applySecurity(basicQuery, secretKey);
      SearchCriteria searchCriteria = new SearchCriteria.Builder(query).build();
      var results = luceneQueryExecutor.execute(IndexName.SUBMISSION, searchCriteria);
      return results.totalHits() == 1
          ? Optional.ofNullable(results.results().getFirst())
          : Optional.empty();
    } catch (IOException e) {
      log.warn("Failed to fetch submission {}: {}", accession, e.getMessage(), e);
      return Optional.empty();
    }
  }

  /** Builds an exact-match query for the submission ID field. */
  private Query buildQueryWithoutSecurity(String accession) {
    return new TermQuery(new Term(SubmissionField.ID.getName(), accession));
  }

  /**
   * Checks if a submission document exists in the index (ignores security).
   *
   * @param accession accession number (e.g. S-BSST1432)
   * @return true if exactly one document exists
   */
  public boolean isDocumentPresent(String accession) {
    try {
      Query query = buildQueryWithoutSecurity(accession);
      SearchCriteria searchCriteria = new SearchCriteria.Builder(query).build();
      var results = luceneQueryExecutor.execute(IndexName.SUBMISSION, searchCriteria);
      return results.totalHits() == 1;
    } catch (IOException ex) {
      log.error("Problem checking existence of document {}", accession, ex);
    }
    return false;
  }
}
