package uk.ac.ebi.biostudies.index_service.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

@Schema(description = "Request to queue multiple submissions for indexing")
public class BatchIndexRequest {

  @NotNull(message = "accNos cannot be null")
  @Size(min = 1, max = 100, message = "accNos must contain 1-100 items")
  @Valid
  private final List<@NotNull String> accNos;

  // Private constructor for builder only
  private BatchIndexRequest(List<String> accNos) {
    this.accNos = accNos;
  }

  // Static factory method with validation
  public static BatchIndexRequest of(List<String> inputAccNos) {
    if (inputAccNos == null || inputAccNos.isEmpty()) {
      throw new IllegalArgumentException("accNos cannot be null or empty");
    }

    // Filter null/empty/whitespace-only, deduplicate (preserve order)
    Set<String> seen = new java.util.LinkedHashSet<>();
    List<String> cleaned =
        inputAccNos.stream()
            .filter(s -> s != null && !s.trim().isEmpty())
            .map(String::trim)
            .filter(seen::add)
            .toList();

    if (cleaned.isEmpty()) {
      throw new IllegalArgumentException("No valid accNos after cleaning");
    }
    if (cleaned.size() > 100) {
      throw new IllegalArgumentException("accNos exceeds max size of 100 after deduplication");
    }

    return new BatchIndexRequest(List.copyOf(cleaned));
  }

  public List<String> accNos() {
    return accNos;
  }

  public int cleanedSize() {
    return accNos.size();
  }

}
