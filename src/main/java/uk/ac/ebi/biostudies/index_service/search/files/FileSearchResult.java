package uk.ac.ebi.biostudies.index_service.search.files;

import java.util.List;

/**
 * Service-layer result object.
 */
public record FileSearchResult(
    long totalFiles,
    long filteredFiles,
    List<FileMetadata> files
) {
  // Static helper to return an empty record
  public static FileSearchResult empty() {
    return new FileSearchResult(0, 0, List.of());
  }
}
