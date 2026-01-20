package uk.ac.ebi.biostudies.index_service.messages;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing WebSocket connection lifecycle and health monitoring.
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Opening and closing WebSocket connections to RabbitMQ STOMP
 *   <li>Periodic health checks via a watchdog that monitors connection status
 *   <li>Automatic reconnection with exponential backoff when connections fail
 * </ul>
 */
@Service
public class WebSocketConnectionService {
  private static final Logger logger = LogManager.getLogger(WebSocketConnectionService.class);

  private static final int MAX_ATTEMPTS = 10;
  private static final long INITIAL_BACKOFF_MS = 1000; // 1 second
  private static final long MAX_BACKOFF_MS = 300000; // 5 minutes

  private final RabbitMQStompService rabbitMQStompService;
  private final RabbitMqConfig rabbitMqConfig;
  private final AtomicBoolean connected = new AtomicBoolean(false);

  private int reconnectionAttempts = 0;
  private long lastAttemptTime = 0;

  public WebSocketConnectionService(
      RabbitMQStompService rabbitMQStompService, RabbitMqConfig rabbitMqConfig) {
    this.rabbitMQStompService = rabbitMQStompService;
    this.rabbitMqConfig = rabbitMqConfig;
  }

  /**
   * Opens the WebSocket connection to RabbitMQ STOMP if enabled in configuration.
   *
   * <p>This method verifies that STOMP messaging is enabled before attempting to establish the
   * connection.
   *
   * <p>Regardless of the `enabled` flag, the internal state is updated to reflect the
   * connected status. This could be done by a separated method for semantics, but works fine here
   *
   * <p>This method is thread-safe due to the use of {@link AtomicBoolean} for state management.
   */
  public void openWebsocket() {
    // Setting connected status as true, as this does not depend on the enabled flag.
    connected.set(true);

    if (!rabbitMqConfig.getEnabled()) {
      logger.warn("WebSocket opening skipped - STOMP messaging is disabled");
      return;
    }

    logger.info("Opening WebSocket connection...");
    rabbitMQStompService.startWebSocket();

    logger.info("WebSocket connection opened successfully");
  }

  /**
   * Closes the WebSocket connection if STOMP messaging is enabled.
   *
   * <p>Upon successful closure, the internal connection state is updated to reflect the
   * disconnected status.
   */
  public void closeWebsocket() {
    if (!rabbitMqConfig.getEnabled()) {
      logger.debug("WebSocket closing skipped - STOMP messaging is disabled");
      return;
    }

    logger.info("Closing WebSocket connection...");
    rabbitMQStompService.stopWebSocket();
    connected.set(false);
    logger.info("WebSocket connection closed");
  }

  /**
   * Checks if the WebSocket connection is currently closed.
   *
   * @return true if connection is closed, false if open
   */
  public boolean isClosed() {
    return !connected.get();
  }

  /**
   * Periodic health check watchdog that monitors WebSocket connectivity.
   *
   * <p>This scheduled task runs at intervals defined by configuration properties and performs the
   * following checks:
   *
   * <ul>
   *   <li>Verifies STOMP messaging is enabled
   *   <li>Checks if the session is already connected
   *   <li>Attempts reconnection with exponential backoff if disconnected
   *   <li>Stops after reaching maximum reconnection attempts
   * </ul>
   *
   * <p>The watchdog automatically resets reconnection attempts upon successful connection.
   */
  @Scheduled(
      fixedDelayString = "${scheduling.stomp.health-check-interval:300000}",
      initialDelayString = "${scheduling.stomp.health-check-delay:600000}")
  public void webSocketWatchDog() {
    if (!rabbitMqConfig.getEnabled() || rabbitMQStompService.isSessionConnected() || !isClosed()) {
      reconnectionAttempts = 0;
      lastAttemptTime = 0;
      return;
    }

    if (reconnectionAttempts >= MAX_ATTEMPTS) {
      logger.error(
          "Max reconnection attempts ({}) reached. Manual intervention required.", MAX_ATTEMPTS);
      return;
    }

    long backoffDelay = calculateBackoff(reconnectionAttempts);
    if (shouldAttemptReconnect(backoffDelay)) {
      attemptReconnection();
    }
  }

  /**
   * Calculates exponential backoff delay with jitter to prevent thundering herd problem.
   *
   * <p>Formula: min(INITIAL_BACKOFF * 2^attempt + random_jitter, MAX_BACKOFF)
   *
   * @param attempt the current reconnection attempt number (zero-based)
   * @return the calculated delay in milliseconds
   */
  private long calculateBackoff(int attempt) {
    long exponentialDelay = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt);
    long jitter = (long) (Math.random() * 1000); // 0-1000ms random jitter
    return Math.min(exponentialDelay + jitter, MAX_BACKOFF_MS);
  }

  /**
   * Determines if enough time has passed since the last reconnection attempt based on the
   * exponential backoff strategy.
   *
   * @param backoffDelay the required delay in milliseconds before next attempt
   * @return true if reconnection should be attempted now, false otherwise
   */
  private boolean shouldAttemptReconnect(long backoffDelay) {
    long currentTime = System.currentTimeMillis();
    if (lastAttemptTime == 0) {
      return true; // First attempt
    }

    long timeSinceLastAttempt = currentTime - lastAttemptTime;
    return timeSinceLastAttempt >= backoffDelay;
  }

  /**
   * Attempts to reconnect to the WebSocket and tracks the attempt count and timing.
   *
   * <p>On successful reconnection, attempt counters are reset. On failure, the attempt is logged
   * and counters are incremented for exponential backoff calculation.
   */
  private void attemptReconnection() {
    try {
      lastAttemptTime = System.currentTimeMillis();
      reconnectionAttempts++;

      logger.info(
          "Attempting WebSocket reconnection (attempt {}/{})", reconnectionAttempts, MAX_ATTEMPTS);

      openWebsocket();

      // If we reach here, connection succeeded
      reconnectionAttempts = 0;
      lastAttemptTime = 0;
      logger.info("WebSocket connection recovered by watchdog");

    } catch (Exception e) {
      logger.warn("Reconnection attempt {} failed: {}", reconnectionAttempts, e.getMessage());

      if (reconnectionAttempts >= MAX_ATTEMPTS) {
        logger.error("Max reconnection attempts reached. Notifying administrators...");
        // TODO: Add alerting mechanism here
      }
    }
  }
}
