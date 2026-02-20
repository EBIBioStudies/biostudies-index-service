package uk.ac.ebi.biostudies.index_service.rest;

import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.biostudies.index_service.exceptions.SubmissionNotAccessibleException;
import uk.ac.ebi.biostudies.index_service.model.IndexedSubmission;
import uk.ac.ebi.biostudies.index_service.submission.SubmissionRetriever;

/** REST controller for retrieving individual submissions by accession. */
@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

  private final SubmissionRetriever retriever;

  public SubmissionController(SubmissionRetriever retriever) {
    this.retriever = retriever;
  }

  /**
   * Retrieves a submission by accession.
   *
   * <ul>
   *   <li><b>200 OK</b>: Submission found and accessible
   *   <li><b>403 Forbidden</b>: Submission exists but user lacks permission (handled
   *       by @ExceptionHandler)
   *   <li><b>404 Not Found</b>: Submission does not exist
   * </ul>
   *
   * @param accession submission accession (e.g. S-BSST1432)
   * @param secretKey optional secret for unreleased submissions
   * @param type optional type filter (e.g. "Study"); if provided, validates exact match
   *     (case-insensitive)
   * @return the submission or appropriate error status
   */
  @GetMapping("/{accession}")
  public ResponseEntity<IndexedSubmission> getSubmission(
      @PathVariable String accession,
      @RequestParam(required = false) String secretKey,
      @RequestParam(required = false) String type)
      throws SubmissionNotAccessibleException {

    // Case 1: Submission found and accessible
    Optional<IndexedSubmission> submissionOpt;
    if (type == null) {
      submissionOpt = retriever.getSubmissionByAccession(accession, secretKey);
    } else {
      submissionOpt = retriever.getSubmissionByAccessionAndType(accession, secretKey, type);
    }

    if (submissionOpt.isPresent()) {
      return ResponseEntity.ok(submissionOpt.get());
    }

    // Case 2 & 3: Distinguish "not found" from "no permissions"
    // retriever already throws SubmissionNotAccessibleException for permission issues
    // If we reach here, it's truly "not found" (404)
    return ResponseEntity.notFound().build();
  }
}
