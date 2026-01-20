package uk.ac.ebi.biostudies.index_service.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Filter criteria for paginated extended submission fetches. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class ExtSubmissionFilters {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  private final String collection;
  private final String fromRTime; // YYYY-MM-DD or null
  private final String toRTime; // YYYY-MM-DD or null
  private final Boolean released; // true/false or null (any)

  /**
   * Creates filters for recent submissions.
   */
  public static ExtSubmissionFilters recent(String collection, int daysBack) {
    LocalDate toDate = LocalDate.now();
    LocalDate fromDate = toDate.minusDays(daysBack);
    return new ExtSubmissionFilters(
        null,
        fromDate.format(DATE_FORMATTER),
        toDate.format(DATE_FORMATTER),
        true);
  }

  /**
   * Builds query parameters string for the backend endpoint.
   */
  public String toQueryParams(int offset, int limit) {
    StringBuilder params = new StringBuilder()
        .append("?offset=").append(offset)
        .append("&limit=").append(limit);

    if (collection != null && !collection.trim().isBlank()) {
      params.append("&collection=").append(encode(collection.trim()));
    }
    if (fromRTime != null && !fromRTime.isBlank()) {
      params.append("&fromRTime=").append(encode(fromRTime));
    }
    if (toRTime != null && !toRTime.isBlank()) {
      params.append("&toRTime=").append(encode(toRTime));
    }
    if (released != null) {
      params.append("&released=").append(released);
    }

    return params.toString();
  }

  private static String encode(String value) {
    return value == null ? null : URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
