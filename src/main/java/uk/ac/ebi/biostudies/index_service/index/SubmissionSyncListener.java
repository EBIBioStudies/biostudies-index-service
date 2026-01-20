package uk.ac.ebi.biostudies.index_service.index;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.client.ExtSubmissionHttpClient;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;
import uk.ac.ebi.biostudies.index_service.client.ExtSubmissionFetchResult;
import uk.ac.ebi.biostudies.index_service.client.ExtSubmissionFetchStatus;

/**
 * Component responsible for listening to RabbitMQ queue messages containing submission update
 * notifications. Processes each message asynchronously by extracting the submission accession and
 * external tab URL, fetching the external submission status, and either deleting the submission
 * from the index if not found or queuing it for indexing if available.
 *
 * <p>Manages an HTTP client with optional proxy configuration based on security settings. Performs
 * cleanup of resources on bean destruction.
 *
 * <p>Expects JSON messages with mandatory fields:
 *
 * <ul>
 *   <li>{@code accNo}: Submission accession (e.g., "S-BSST1432")
 *   <li>{@code extTabUrl}: URL to fetch external submission data
 * </ul>
 *
 * @see IndexingService
 * @see SubmissionIndexer
 * @see ExtSubmissionHttpClient
 */
@Slf4j
@Component
public class SubmissionSyncListener {

  private final SubmissionIndexer submissionIndexer;
  private final IndexingService indexingService;
  private final SecurityConfig securityConfig;
  private final CloseableHttpClient httpClient;
  private final ExtSubmissionHttpClient extSubmissionHttpClient;

  /**
   * Constructs the listener with required dependencies and initializes the HTTP client.
   *
   * @param submissionIndexer service for direct index operations like deletion
   * @param indexingService service for queuing submissions for indexing
   * @param securityConfig configuration providing proxy settings if needed
   * @param extSubmissionHttpClient client for fetching external submission data
   */
  public SubmissionSyncListener(
      SubmissionIndexer submissionIndexer,
      IndexingService indexingService,
      SecurityConfig securityConfig,
      ExtSubmissionHttpClient extSubmissionHttpClient) {
    this.submissionIndexer = submissionIndexer;
    this.indexingService = indexingService;
    this.securityConfig = securityConfig;
    this.extSubmissionHttpClient = extSubmissionHttpClient;
    this.httpClient = createHttpClient();
  }

  /**
   * Creates a configured HTTP client, optionally setting proxy from security configuration. Used
   * internally for potential future HTTP operations, though primarily relies on injected client.
   *
   * @return configured CloseableHttpClient instance
   */
  private CloseableHttpClient createHttpClient() {
    HttpClientBuilder clientBuilder = HttpClients.custom();

    if (securityConfig.isProxyConfigured()) {
      clientBuilder.setProxy(
          new HttpHost(securityConfig.getProxyHost(), securityConfig.getProxyPort()));
    }

    return clientBuilder.build();
  }

  /**
   * Asynchronously processes a submission update message from the queue. Extracts accession and
   * URL, fetches external submission, deletes if not found, or queues for indexing.
   *
   * @param message JSON node containing {@code accNo} and {@code extTabUrl}
   * @throws RuntimeException if processing fails unexpectedly
   */
  @Async
  public void processSubmissionUpdate(JsonNode message) {
    String accession = null;
    try {
      accession = extractAccession(message);
      String url = extractUrl(message);

      log.info("Processing submission update for accession: {}", accession);

      ExtSubmissionFetchResult fetchResult = extSubmissionHttpClient.fetchExtSubmissionByUrl(url);

      if (ExtSubmissionFetchStatus.NOT_FOUND.equals(fetchResult.status())) {
        log.info("Submission not found, deleting accession: {}", accession);
        submissionIndexer.deleteSubmission(accession);
      } else {
        log.info("Queuing indexing submission for accession: {}", accession);
        indexingService.queueSubmission(accession);
      }

    } catch (InvalidMessageException e) {
      log.error("Invalid message format: {} - {}", message, e.getMessage());
    } catch (Exception e) {
      log.error(
          "Unexpected error processing message for accession: {}- {}", accession, e.getMessage());
      throw new RuntimeException("Failed to process submission update", e);
    }
  }

  private String extractAccession(JsonNode message) throws InvalidMessageException {
    JsonNode accNode = message.get("accNo");
    if (accNode == null || accNode.asText().isEmpty()) {
      throw new InvalidMessageException("Missing or empty accNo field");
    }
    return accNode.asText();
  }

  private String extractUrl(JsonNode message) throws InvalidMessageException {
    JsonNode urlNode = message.get("extTabUrl");
    if (urlNode == null || urlNode.asText().isEmpty()) {
      throw new InvalidMessageException("Missing or empty extTabUrl field");
    }
    return urlNode.asText();
  }

  @PreDestroy
  public void cleanup() {
    try {
      if (httpClient != null) {
        httpClient.close();
        log.info("HTTP client closed successfully");
      }
    } catch (IOException e) {
      log.error("Error closing HTTP client", e);
    }
  }

  // Custom exceptions for better error handling
  public static class InvalidMessageException extends Exception {
    public InvalidMessageException(String message) {
      super(message);
    }
  }
}
