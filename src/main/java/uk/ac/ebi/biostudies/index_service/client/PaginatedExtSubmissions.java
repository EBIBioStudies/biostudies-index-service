package uk.ac.ebi.biostudies.index_service.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

/** Models a paginated response from the /submissions/extended endpoint. */
@Getter
@AllArgsConstructor(onConstructor_ = @JsonCreator)
@NoArgsConstructor(force = true)
public class PaginatedExtSubmissions {
  private final List<ExtendedSubmissionMetadata> content;
  private final long totalElements;
  private final int limit;
  private final int offset;
  private final String next;
  private final String previous;
}
