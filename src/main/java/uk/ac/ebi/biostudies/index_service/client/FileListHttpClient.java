package uk.ac.ebi.biostudies.index_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;

/**
 * HTTP client for retrieving file list metadata from BioStudies referencedFiles endpoints.
 *
 * <p>Performs authenticated HTTP GET requests to filelist URLs (e.g., {@code
 * /submissions/extended/{accno}/referencedFiles/filelist}) and returns the parsed JSON response
 * containing file metadata arrays. Supports session token authentication and optional proxy
 * configuration.
 */
@Slf4j
@Component
public class FileListHttpClient {

  /** Header name used to propagate the session token for BioStudies REST calls. */
  public static final String X_SESSION_TOKEN = "X-Session-Token";

  private final SecurityConfig securityConfig;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  /**
   * Creates a new client using the given security configuration for authentication and proxy
   * settings.
   *
   * @param securityConfig configuration providing REST token and optional proxy details
   */
  public FileListHttpClient(SecurityConfig securityConfig) {
    this.securityConfig = securityConfig;
  }

  /**
   * Fetches file list metadata from the specified referencedFiles endpoint.
   *
   * <p>Issues an HTTP GET to the provided {@code fileListUrl}, setting the session token header and
   * using the configured HTTP proxy if present. Returns the raw {@link JsonNode} response
   * containing the {@code files} array with file metadata (fileName, md5, size, fullPath, etc.).
   *
   * <p>A {@code 200 OK} response is parsed into a {@link JsonNode}. Any other status code yields a
   * {@code null} result. HTTP errors and parsing failures are logged but do not throw exceptions.
   *
   * @param fileListUrl the referencedFiles endpoint URL (e.g., {@code
   *     /submissions/extended/S-BIAD2077/referencedFiles/filelist})
   * @return parsed JSON response containing file metadata, or {@code null} if request failed
   */
  public JsonNode fetchFileListMetadata(String fileListUrl) {
    Objects.requireNonNull(fileListUrl, "fileListUrl must not be null");
    if (fileListUrl.trim().isEmpty()) {
      log.warn("Empty fileListUrl provided, returning null");
      return null;
    }

    String completeUrl = securityConfig.getBackendBaseURL() + fileListUrl;

    HttpGet httpGet;
    try {
      httpGet = new HttpGet(completeUrl);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid fileListUrl [{}]: {}", completeUrl, e.getMessage());
      return null;
    }

    //httpGet.setHeader(X_SESSION_TOKEN, securityConfig.getPartialUpdateRestToken());
    log.debug("Fetching file list from: {}", completeUrl);

    HttpClientBuilder clientBuilder = HttpClients.custom();
    if (securityConfig.getProxyHost() != null && !securityConfig.getProxyHost().isEmpty()) {
      clientBuilder.setProxy(
          new HttpHost(securityConfig.getProxyHost(), securityConfig.getProxyPort()));
    }

    try (CloseableHttpResponse response = clientBuilder.build().execute(httpGet)) {
      int statusCode = response.getStatusLine().getStatusCode();

      if (statusCode != 200) {
        log.error("Unexpected HTTP {} when fetching file list from {}", statusCode, completeUrl);
        return null;
      }

      if (response.getEntity() == null) {
        log.warn("Empty response body from file list URL: {}", fileListUrl);
        return null;
      }

      String body = EntityUtils.toString(response.getEntity());
      if (body.trim().isEmpty()) {
        log.warn("Blank response body from file list URL: {}", fileListUrl);
        return null;
      }

      JsonNode fileListJson = objectMapper.readTree(body);
      log.debug(
          "Successfully fetched file list with {} files from {}",
          fileListJson.has("files") ? fileListJson.get("files").size() : 0,
          fileListUrl);

      return fileListJson;

    } catch (IOException e) {
      log.error("IO error fetching file list from [{}]: {}", fileListUrl, e.getMessage(), e);
      return null;
    }
  }
}
