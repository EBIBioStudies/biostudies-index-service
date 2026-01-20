package uk.ac.ebi.biostudies.index_service.client;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

/**
 * Result for paginated submission fetches.
 */
@AllArgsConstructor
@Getter
public class PaginatedExtSubmissionFetchResult {
  private final List<ExtendedSubmissionMetadata> submissions;
  private final long totalElements;
  private final boolean hasNext;
  private final String nextUrl;
}
