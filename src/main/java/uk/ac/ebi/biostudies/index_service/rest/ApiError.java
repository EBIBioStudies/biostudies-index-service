package uk.ac.ebi.biostudies.index_service.rest;

public record ApiError(
    String code,
    String field,
    String message,
    Integer httpStatus
) {}