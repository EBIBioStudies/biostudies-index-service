package uk.ac.ebi.biostudies.index_service.index;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uk.ac.ebi.biostudies.index_service.client.ExtSubmissionFilters;

@Data
@Schema(description = "Request to stream index submissions matching filters")
public class StreamIndexRequest {
  @NotNull
  @Schema(description = "Submission filters (collection, release date, etc.)")
  private ExtSubmissionFilters filters;

  @Min(10) @Max(1000) @Schema(defaultValue = "100")
  private Integer pageSize = 100;
}