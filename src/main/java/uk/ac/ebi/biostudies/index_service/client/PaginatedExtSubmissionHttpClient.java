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
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

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
   * Streams all extended submissions matching filters (memory efficient). On fetch failure, skips
   * to next offset and continues.
   *
   * @param filters filter criteria (non-null)
   * @param processor called per successful page (non-null)
   * @param pageSize submissions per page (> 0)
   * @throws IOException on unrecoverable failures
   */
  public void processAllExtSubmissionsStream(
      ExtSubmissionFilters filters, PageProcessor processor, int pageSize)
      throws IOException, SubmissionFetchException {

    Objects.requireNonNull(filters, "filters must not be null");
    Objects.requireNonNull(processor, "processor must not be null");
    if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");

    String baseUrl = securityConfig.getBackendBaseURL().trim();
    if (baseUrl.isBlank()) {
      throw new IllegalArgumentException("Backend base URL not configured");
    }
    String normalizedBase =
        baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

    String nextUrl = normalizedBase + "/submissions/extended" + filters.toQueryParams(0, pageSize);
    int pageIndex = 0;
    long totalElements = -1L;

    while (nextUrl != null) {
      PaginatedExtSubmissions page = null;

      try {
        page = fetchPaginatedPage(nextUrl);
      } catch (SubmissionFetchException e) {
        log.error("Failed to fetch page {} at {}: {}", pageIndex + 1, nextUrl, e.getMessage());
      }

      // Recovery: if fetch failed or page null, skip to next offset
      if (page == null || page.content() == null) {
        log.warn("Skipping page {} (null/empty), advancing to next offset", pageIndex + 1);
        pageIndex++;
        nextUrl =
            normalizedBase
                + "/submissions/extended"
                + filters.toQueryParams(pageIndex * pageSize, pageSize);
        continue;
      }

      processor.process(page);
      totalElements = page.totalElements();

      nextUrl = page.next();
      if (nextUrl != null && filters.getCollection() != null && !nextUrl.contains("collection=")) {
        nextUrl += "&collection=" + filters.getCollection();
      }

      log.info(
          "Streamed page {}: {} subs (next: {}) total: {}",
          ++pageIndex,
          page.content().size(),
          nextUrl != null,
          totalElements);
    }
  }

  /** Fetches a single paginated page by URL. */
  private PaginatedExtSubmissions fetchPaginatedPage(String url)
      throws IOException, SubmissionFetchException {
    JsonNode rawJson = fetchSubmissionJson(url);
    if (rawJson == null) {
      return new PaginatedExtSubmissions(List.of(), 0L, 0, 0, null, null);
    }
    log.info("Processing page: {}", url);

    RawPaginatedResponse rawPage = objectMapper.treeToValue(rawJson, RawPaginatedResponse.class);

    List<ExtendedSubmissionMetadata> content =
        rawPage.content().stream()
            .map(
                rawNode ->
                    ExtendedSubmissionMetadata.fromJsonNode(
                        rawNode, objectMapper)) // Throws SubmissionFetchException
            .toList();

    return new PaginatedExtSubmissions(
        content,
        rawPage.totalElements(),
        rawPage.limit(),
        rawPage.offset(),
        rawPage.next(),
        rawPage.previous());
  }
}
