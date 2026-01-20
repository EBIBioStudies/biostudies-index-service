package uk.ac.ebi.biostudies.index_service.index;

public record IndexingInfo(String accNo, int queuePosition, String taskId, String statusUrl) {}
