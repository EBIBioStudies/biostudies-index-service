package uk.ac.ebi.biostudies.index_service.search.files;

import java.util.Map;

/**
 * Single file metadata holder.
 */
public record FileMetadata(
    String path,
    String type,
    long size,
    Map<String, String> metadata
) {
}
