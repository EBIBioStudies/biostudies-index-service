package uk.ac.ebi.biostudies.index_service.index;

import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record IndexingResult(
    Map<String, Object> valueMap,
    FileIndexingContext fileContext,
    List<String> columns,
    boolean success) {

  public boolean hasErrors() { return !success; }
  public long fileCount() { return fileContext != null ? fileContext.getFileCounter().get() : 0; }
}
