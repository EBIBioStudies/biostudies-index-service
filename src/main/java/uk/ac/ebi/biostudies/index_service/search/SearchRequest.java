package uk.ac.ebi.biostudies.index_service.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class SearchRequest {
  @NotBlank
  private String query = "";

  @Min(1)
  private Integer page = 1;

  @Min(1)
  @Max(100)
  private Integer pageSize = 20;

  private String sortBy = "";

  private String sortOrder = "descending";

  private Map<String, List<String>> facets = new LinkedHashMap<>();

  private Map<String, List<String>> fields = new LinkedHashMap<>();
}
