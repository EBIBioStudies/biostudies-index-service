package uk.ac.ebi.biostudies.index_service.client;

import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

/**
 * @param metadata may be null if not FOUND
 * @param httpStatus optional, for logging/debug
 * @param errorMessage optional, only for ERROR
 */
public record ExtSubmissionFetchResult(
    ExtSubmissionFetchStatus status,
    ExtendedSubmissionMetadata metadata,
    int httpStatus,
    String errorMessage) {}
