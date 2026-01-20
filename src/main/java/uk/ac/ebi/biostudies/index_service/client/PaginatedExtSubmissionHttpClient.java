package uk.ac.ebi.biostudies.index_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;

/** Extension to ExtSubmissionHttpClient for paginated batch fetches. */
@Slf4j
@Component
public class PaginatedExtSubmissionHttpClient extends ExtSubmissionHttpClient {
  private static final int DEFAULT_PAGE_LIMIT = 100;

  private final ObjectMapper objectMapper;

  public PaginatedExtSubmissionHttpClient(SecurityConfig securityConfig) {
    super(securityConfig);
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  /**
   * Fetches all extended submissions matching the filters using pagination.
   *
   * @param filters filter criteria (collection required for now)
   * @return all matching submissions
   * @throws IOException on fetch failures
   */
  //  public PaginatedExtSubmissionFetchResult fetchAllExtSubmissions(ExtSubmissionFilters filters)
  //      throws IOException, SubmissionFetchException {
  //    Objects.requireNonNull(filters, "filters must not be null");
  //
  //    String baseUrl = securityConfig.getBackendBaseURL().trim();
  //    if (baseUrl.isBlank()) {
  //      throw new IllegalArgumentException("Backend base URL not configured");
  //    }
  //    String normalizedBase =
  //        baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  //
  //    List<ExtendedSubmissionMetadata> allSubmissions = new java.util.ArrayList<>();
  //    String nextUrl =
  //        normalizedBase + "/submissions/extended" + filters.toQueryParams(0, DEFAULT_PAGE_LIMIT);
  //
  //    while (nextUrl != null) {
  //      PaginatedExtSubmissions page = fetchPaginatedPage(nextUrl);
  //      if (page.getContent() != null) {
  //        allSubmissions.addAll(page.getContent());
  //      }
  //      nextUrl = page.getNext();
  //      System.out.println("nextUrl: " + nextUrl);
  //      assert page.getContent() != null;
  //      log.debug(
  //          "Fetched {} submissions from offset {}, total so far: {}",
  //          page.getContent().size(),
  //          page.getOffset(),
  //          allSubmissions.size());
  //    }
  //
  //    return new PaginatedExtSubmissionFetchResult(
  //        allSubmissions, allSubmissions.size(), false, null);
  //  }

  /**
   * Streams all pages matching filters (memory efficient - no full load).
   *
   * @param filters filter criteria
   * @param processor called for each page (100 submissions/page)
   * @param pageSize submissions per page (default: 100)
   * @throws IOException on fetch failures
   */
  public void processAllExtSubmissionsStream(
      ExtSubmissionFilters filters, PageProcessor processor, int pageSize)
      throws IOException, SubmissionFetchException {
    Objects.requireNonNull(filters, "filters must not be null");
    Objects.requireNonNull(processor, "processor must not be null");

    String baseUrl = securityConfig.getBackendBaseURL().trim();
    if (baseUrl.isBlank()) {
      throw new IllegalArgumentException("Backend base URL not configured");
    }
    String normalizedBase =
        baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

    String nextUrl = normalizedBase + "/submissions/extended" + filters.toQueryParams(0, pageSize);

    int pageNum = 0;
    while (nextUrl != null) {
      PaginatedExtSubmissions page = fetchPaginatedPage(nextUrl);
      processor.process(page); // Process immediately (no buffering)

      nextUrl = page.getNext();
      log.debug(
          "Streamed page {}: {} submissions (next: {})",
          ++pageNum,
          page.getContent().size(),
          nextUrl != null);
    }
  }

  /** Fetches a single paginated page by URL. */
  private PaginatedExtSubmissions fetchPaginatedPage(String url)
      throws IOException, SubmissionFetchException {
    JsonNode rawJson = fetchSubmissionJson(url);
    if (rawJson == null) {
      return new PaginatedExtSubmissions(List.of(), 0L, 0, 0, null, null);
    }
    return objectMapper.treeToValue(rawJson, PaginatedExtSubmissions.class);
  }
}
