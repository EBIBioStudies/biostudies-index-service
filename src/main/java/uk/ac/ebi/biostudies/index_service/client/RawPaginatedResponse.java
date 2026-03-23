package uk.ac.ebi.biostudies.index_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record RawPaginatedResponse(
    List<JsonNode> content,
    long totalElements,
    int limit,
    int offset,
    String next,
    String previous
) {}