package uk.ac.ebi.biostudies.index_service.client;

import java.util.List;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

/** Models a paginated response from the /submissions/extended endpoint. */
public record PaginatedExtSubmissions(
    List<ExtendedSubmissionMetadata> content,
    long totalElements,
    int limit,
    int offset,
    String next,
    String previous) {}
