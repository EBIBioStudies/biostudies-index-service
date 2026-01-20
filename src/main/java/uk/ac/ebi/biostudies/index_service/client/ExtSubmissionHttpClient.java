package uk.ac.ebi.biostudies.index_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;
import uk.ac.ebi.biostudies.index_service.model.ExtendedSubmissionMetadata;

/**
 * HTTP client for retrieving extended submission representations (ExtSubmission) as JSON.
 *
 * <p>This client performs authenticated HTTP GET requests to the {@code extTabUrl} endpoint,
 * optionally via a proxy as configured in {@link SecurityConfig}, with retry logic for resilience.
 */
@Slf4j
@Component
public class ExtSubmissionHttpClient {

  /** Header name used to propagate the session token for partial-update REST calls. */
  public static final String X_SESSION_TOKEN = "X-Session-Token";

  private static final int MAX_RETRIES = 3;
  private static final long BASE_RETRY_DELAY_MS = 1000;

  protected final SecurityConfig securityConfig;
  private final ObjectMapper objectMapper;
  private final CloseableHttpClient httpClient;

  public ExtSubmissionHttpClient(SecurityConfig securityConfig) {
    this.securityConfig = securityConfig;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    this.httpClient = createHttpClient();
  }

  private CloseableHttpClient createHttpClient() {
    HttpClientBuilder clientBuilder = HttpClients.custom();
    if (securityConfig.getProxyHost() != null && !securityConfig.getProxyHost().isEmpty()) {
      clientBuilder.setProxy(
          new HttpHost(securityConfig.getProxyHost(), securityConfig.getProxyPort()));
    }
    return clientBuilder.build();
  }

  /**
   * Fetches raw submission JSON from the given URL—core method with retries.
   *
   * <p>Returns {@code null} on 404 (signals deletion), parsed {@link JsonNode} on 200. Retries IO
   * failures; throws {@link SubmissionFetchException} on permanent errors/client errors.
   */
  public JsonNode fetchSubmissionJson(String url) throws SubmissionFetchException {
    if (url == null || url.isBlank()) {
      throw new SubmissionFetchException("URL must not be blank");
    }

    HttpGet httpGet = new HttpGet(url);
    httpGet.setHeader(X_SESSION_TOKEN, securityConfig.getPartialUpdateRestToken());

    int attempt = 0;
    Exception lastException = null;

    while (attempt < MAX_RETRIES) {
      try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode == HttpStatus.SC_NOT_FOUND) {
          return null;
        }

        if (statusCode == HttpStatus.SC_OK) {
          String body = EntityUtils.toString(response.getEntity());
          if (body.isBlank()) {
            throw new SubmissionFetchException("Blank response body from " + url);
          }
          return objectMapper.readTree(body);
        }

        // Client errors (4xx except 404) fail immediately
        if (statusCode >= 400 && statusCode < 500) {
          throw new SubmissionFetchException(
              "Client error fetching submission: HTTP " + statusCode);
        }

        log.warn("Unexpected status {} for URL: {}", statusCode, url);

      } catch (Exception e) {
        lastException = e;
        attempt++;
        if (attempt < MAX_RETRIES) {
          long delay = BASE_RETRY_DELAY_MS * attempt;
          log.warn(
              "Attempt {}/{} failed for URL {}, retrying after {}ms - {}",
              attempt,
              MAX_RETRIES,
              url,
              delay,
              e.getMessage());
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SubmissionFetchException("Interrupted during retry", ie);
          }
        }
      }
    }
    throw new SubmissionFetchException(
        "Failed to fetch submission after " + MAX_RETRIES + " attempts", lastException);
  }

  /**
   * Fetches extended submission by accession number.
   *
   * @param accNo submission accession (e.g., "S-BSST123")
   * @return fetch result
   * @throws IOException on fetch failures
   * @throws IllegalArgumentException if accNo invalid
   */
  public ExtSubmissionFetchResult fetchExtSubmissionByAccNo(String accNo) throws IOException {
    Objects.requireNonNull(accNo, "accNo must not be null");
    if (accNo.trim().isBlank()) {
      throw new IllegalArgumentException("accNo must not be blank");
    }

    String baseUrl = securityConfig.getBackendBaseURL().trim();
    if (baseUrl.isBlank()) {
      throw new IllegalArgumentException("Backend base URL not configured");
    }

    // Normalize: ensure single slash between parts
    String normalizedBase =
        baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String normalizedAccNo = accNo.trim();

    String url = String.format("%s/submissions/extended/%s", normalizedBase, normalizedAccNo);

    log.debug("Fetching ExtSubmission for {} -> {}", accNo, url);
    return fetchExtSubmissionByUrl(url);
  }

  /** Fetches typed metadata result—delegates to raw fetch, adds deserialization. */
  public ExtSubmissionFetchResult fetchExtSubmissionByUrl(String url) throws IOException {
    try {
      JsonNode rawJson = fetchSubmissionJson(url);
      if (rawJson == null) {
        return new ExtSubmissionFetchResult(ExtSubmissionFetchStatus.NOT_FOUND, null, HttpStatus.SC_NOT_FOUND, null);
      }
      ExtendedSubmissionMetadata metadata =
          objectMapper.treeToValue(rawJson, ExtendedSubmissionMetadata.class);
      metadata.setRawSubmissionJson(rawJson);
      return new ExtSubmissionFetchResult(ExtSubmissionFetchStatus.FOUND, metadata, HttpStatus.SC_OK, null);
    } catch (SubmissionFetchException e) {
      if (e.getMessage().contains("HTTP 404")) {
        return new ExtSubmissionFetchResult(ExtSubmissionFetchStatus.NOT_FOUND, null, HttpStatus.SC_NOT_FOUND, null);
      }
      throw new IOException("Failed to fetch ExtSubmission: " + e.getMessage(), e);
    }
  }

  @PreDestroy
  public void cleanup() {
    try {
      httpClient.close();
      log.info("HTTP client closed successfully");
    } catch (IOException e) {
      log.error("Error closing HTTP client", e);
    }
  }

  /** Exception for submission fetch failures, matching listener contract. */
  public static class SubmissionFetchException extends Exception {
    public SubmissionFetchException(String message) {
      super(message);
    }

    public SubmissionFetchException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
