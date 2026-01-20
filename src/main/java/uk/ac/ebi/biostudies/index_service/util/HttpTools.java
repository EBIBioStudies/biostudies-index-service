package uk.ac.ebi.biostudies.index_service.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;

public class HttpTools {

  private static HttpClient makeConnection(SecurityConfig securityConfig) {
    return HttpClient.newBuilder()
        // Do not use a pooled executor; the FTP-over-HTTP server drops pooled connections.
        .version(HttpClient.Version.HTTP_2)
        .proxy(getProxySelector(securityConfig)) // Apply proxy settings
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  private static ProxySelector getProxySelector(SecurityConfig securityConfig) {
    return securityConfig.isProxyConfigured()
        ? ProxySelector.of(
        new InetSocketAddress(securityConfig.getProxyHost(), securityConfig.getProxyPort()))
        : ProxySelector.getDefault();
  }

  /**
   * Fetches a large file as a streaming {@link InputStream} using HTTP.
   *
   * @param url full URL of the resource to fetch
   * @param securityConfig proxy / security configuration
   * @return an {@link InputStream} to read the response body
   * @throws IOException if the request fails or returns a non-200 status
   * @throws InterruptedException if the calling thread is interrupted while waiting
   */
  public static InputStream fetchLargeFileStream(String url, SecurityConfig securityConfig)
      throws IOException, InterruptedException {

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

    HttpResponse<InputStream> response =
        makeConnection(securityConfig).send(request, HttpResponse.BodyHandlers.ofInputStream());

    if (response.statusCode() != 200) {
      // Close body if present, then throw
      try (InputStream body = response.body()) {
        // just to ensure connection is properly released
      }
      throw new IOException(
          "Failed to retrieve file. HTTP response code: " + response.statusCode());
    }

    return response.body();
  }
}
