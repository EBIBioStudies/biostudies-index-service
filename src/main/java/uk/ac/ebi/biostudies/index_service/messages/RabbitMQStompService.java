package uk.ac.ebi.biostudies.index_service.messages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import uk.ac.ebi.biostudies.index_service.index.SubmissionSyncListener;

/**
 * Service for managing STOMP (Simple Text Oriented Messaging Protocol) connections to RabbitMQ.
 *
 * <p>This service handles WebSocket-based STOMP messaging for receiving BioStudies submission
 * updates from RabbitMQ. It manages connection lifecycle, automatic reconnection on failures, and
 * subscription to configured routing keys.
 *
 * <p>The service can be controlled via {@link #startWebSocket()}, {@link #stopWebSocket()}, and
 * {@link #isSessionConnected()} methods.
 *
 * @see RabbitMqConfig
 * @see RabbitMqMessagingConfig
 */
@Slf4j
@Service
public class RabbitMQStompService {

  private static final int RECONNECTION_DELAY_SECONDS = 10;

  private final RabbitMqConfig rabbitMqConfig;
  private final RabbitMqMessagingConfig messagingConfig;
  private final SubmissionSyncListener submissionSyncListener;
  private final ObjectMapper objectMapper;

  private volatile StompSession stompSession;
  private ThreadPoolTaskScheduler taskScheduler;
  private volatile boolean isShuttingDown = false;

  public RabbitMQStompService(
      RabbitMqConfig rabbitMqConfig,
      RabbitMqMessagingConfig messagingConfig,
      SubmissionSyncListener submissionSyncListener) {
    this.rabbitMqConfig = rabbitMqConfig;
    this.messagingConfig = messagingConfig;
    this.submissionSyncListener = submissionSyncListener;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Checks if the STOMP session is currently active and connected.
   *
   * @return {@code true} if the session exists and is connected, {@code false} otherwise
   */
  public boolean isSessionConnected() {
    return stompSession != null && stompSession.isConnected();
  }

  /**
   * Stops the active STOMP WebSocket connection if one exists.
   *
   * <p>This method gracefully disconnects the current session and logs the disconnection. If no
   * active session exists, this method does nothing.
   */
  public void stopWebSocket() {
    if (stompSession != null && stompSession.isConnected()) {
      stompSession.disconnect();
      stompSession = null;
      log.info("STOMP session disconnected");
    }
  }

  /**
   * Starts a new STOMP WebSocket connection if one is not already active.
   *
   * <p>This method checks if a session exists and is connected before attempting to initialize a
   * new connection. If a connection already exists, this method does nothing.
   */
  public void startWebSocket() {
    if (stompSession == null || !stompSession.isConnected()) {
      init();
    }
  }

  /**
   * Initializes the STOMP client for connecting to the RabbitMQ STOMP/WebSocket endpoint.
   *
   * <p>If STOMP messaging is disabled in configuration, this method performs no action. Otherwise,
   * it creates and configures a WebSocket STOMP client using a string message converter for
   * receiving messages, prepares a task scheduler, and sets login and version STOMP headers. The
   * client connection is asynchronous, and both successful connection and connection failure are
   * logged.
   *
   * <p>Example JSON messages expected by the client:
   *
   * <pre>
   * {
   *   "accNo": "{S-xxx}",
   *   "pagetabUrl": "http://server:port/submissions/{S-xxx}/.json",
   *   "extTabUrl": "http://server:port/submissions/extended/{S-xxx}"
   * }
   * </pre>
   *
   * @see org.springframework.web.socket.messaging.WebSocketStompClient
   * @see java.util.concurrent.CompletableFuture
   */
  public void init() {
    log.debug("Initiating STOMP client service");

    if (!rabbitMqConfig.getEnabled()) {
      log.debug("STOMP client is disabled");
      return;
    }

    if (isShuttingDown) {
      log.debug("Service is shutting down, skipping connection attempt");
      return;
    }

    log.debug("STOMP client is enabled");

    String host = rabbitMqConfig.getHost();
    Integer port = rabbitMqConfig.getPort();

    if (host == null || host.isBlank() || port == null) {
      log.error("STOMP connection failed: host or port is not configured properly. host: '{}', port: '{}'", host, port);
      return;
    }

    String url = String.format("ws://%s:%s/ws", host, port);
    log.info("Connecting to STOMP broker at: {}", url);

    WebSocketClient client = new StandardWebSocketClient();
    WebSocketStompClient stompClient = new WebSocketStompClient(client);

    // Use StringMessageConverter as messages are received as strings and parsed manually
    stompClient.setMessageConverter(new StringMessageConverter());

    // Initialize or reuse task scheduler
    if (taskScheduler == null) {
      taskScheduler = new ThreadPoolTaskScheduler();
      taskScheduler.afterPropertiesSet();
    }
    stompClient.setTaskScheduler(taskScheduler);

    // Configure STOMP connection headers
    StompHeaders stompHeaders = new StompHeaders();
    stompHeaders.setLogin(rabbitMqConfig.getLoginHeader());
    stompHeaders.setPasscode(rabbitMqConfig.getPasswordHeader());
    stompHeaders.setAcceptVersion("1.1", "1.2");

    RabbitMQStompSessionHandler sessionHandler = new RabbitMQStompSessionHandler();

    // Asynchronously connect to the broker
    CompletableFuture<StompSession> sessionFuture =
        stompClient.connectAsync(url, new WebSocketHttpHeaders(), stompHeaders, sessionHandler);

    // Handle connection success and failure
    sessionFuture
        .thenAccept(
            session -> {
              this.stompSession = session;
              log.info("STOMP session established: {}", session.getSessionId());
            })
        .exceptionally(
            ex -> {
              log.error("Failed to connect to STOMP broker at {}: {}", url, ex.getMessage());
              // Attempt reconnection if not shutting down
              if (!isShuttingDown && rabbitMqConfig.getEnabled()) {
                scheduleReconnection();
              }
              return null;
            });

    log.debug("STOMP client connection initiated to {}", url);
  }

  /**
   * Schedules a reconnection attempt after a delay.
   *
   * <p>This method is called when a connection attempt fails or when the connection is lost. It
   * waits for a configured delay before attempting to reconnect to avoid overwhelming the broker
   * with rapid reconnection attempts.
   */
  private void scheduleReconnection() {
    log.info("Scheduling reconnection attempt in {} seconds...", RECONNECTION_DELAY_SECONDS);
    CompletableFuture.runAsync(
        () -> {
          try {
            TimeUnit.SECONDS.sleep(RECONNECTION_DELAY_SECONDS);
            if (!isShuttingDown) {
              log.info("Attempting to reconnect...");
              startWebSocket();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Reconnection attempt interrupted");
          }
        });
  }

  /**
   * Cleanup method called when the service is destroyed. Ensures proper shutdown of the task
   * scheduler and STOMP session.
   */
  @PreDestroy
  public void destroy() {
    log.info("Shutting down RabbitMQ STOMP service");
    isShuttingDown = true;

    stopWebSocket();

    if (taskScheduler != null) {
      taskScheduler.shutdown();
      log.info("Task scheduler shut down");
    }
  }

  /**
   * STOMP session handler for managing connections to RabbitMQ and processing submission messages.
   *
   * <p>This handler automatically subscribes to BioStudies exchange routing keys upon connection,
   * processes incoming JSON messages via {@link JsonNode}, and implements reconnection logic on
   * transport errors.
   */
  protected class RabbitMQStompSessionHandler extends StompSessionHandlerAdapter {

    /**
     * Specifies the payload type for incoming STOMP messages. Messages are deserialized as strings
     * and then manually parsed as JSON.
     *
     * @param headers the STOMP message headers
     * @return the type to deserialize the payload into (String.class)
     */
    @Override
    public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
      return String.class;
    }

    /**
     * Called after the STOMP session is successfully established.
     *
     * <p>This method constructs a unique queue name by appending the hostname to the base queue
     * name, then subscribes to the configured routing keys for partial and published submissions.
     * The session reference is stored for connection status checks.
     *
     * @param session the established STOMP session
     * @param connectedHeaders headers from the STOMP CONNECTED frame
     */
    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
      String submissionPartialQueue = messagingConfig.getPartialSubmissionQueue();

      // Append hostname to queue name for uniqueness across instances
      try {
        String hostname = InetAddress.getLocalHost().getHostName();
        submissionPartialQueue = submissionPartialQueue + "-" + hostname;
      } catch (UnknownHostException e) {
        log.error("Failed to get hostname, using queue name without hostname suffix", e);
      }

      // Store session reference if not already set by the CompletableFuture
      // This ensures the session is available as soon as the connection is established
      if (stompSession == null) {
        stompSession = session;
      }

      log.info(
          "STOMP connection established - session: {}, server: {}",
          connectedHeaders.getFirst("session"),
          connectedHeaders.getFirst("server"));

      // Subscribe to routing keys
      subscribeToRoutingKey(submissionPartialQueue, messagingConfig.getPublishedRoutingKey());
      subscribeToRoutingKey(submissionPartialQueue, messagingConfig.getPartialsRoutingKey());

      log.info("STOMP client subscribed to queue: {}", submissionPartialQueue);
    }

    /**
     * Subscribes to a specific routing key on the BioStudies exchange.
     *
     * <p>Creates a durable, non-auto-delete subscription to ensure messages are not lost when the
     * client disconnects. The subscription uses a named queue to allow multiple consumers to share
     * the same queue.
     *
     * @param queueName the name of the queue to bind
     * @param routingKey the routing key to subscribe to
     */
    private void subscribeToRoutingKey(String queueName, String routingKey) {
      StompHeaders headers = new StompHeaders();
      headers.setDestination("/exchange/" + messagingConfig.getExchangeName() + "/" + routingKey);
      headers.set("x-queue-name", queueName);
      headers.set("auto-delete", "false");
      headers.set("durable", "true");

      stompSession.subscribe(headers, this);
      log.debug("Subscribed to routing key: {} on queue: {}", routingKey, queueName);
    }

    /**
     * Handles transport-level errors such as network failures or connection drops.
     *
     * <p>When a transport error occurs, this method logs the error and attempts to reconnect if the
     * service is not shutting down.
     *
     * @param session the STOMP session where the error occurred
     * @param exception the transport exception
     */
    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      log.error("STOMP transport error occurred: {}", exception.getMessage());

      // Check if it's a NOT_FOUND error related to the exchange
      if (exception.getMessage() != null && exception.getMessage().contains("not_found") && exception.getMessage().contains("biostudies-exchange")) {
        log.error("CRITICAL: The required exchange 'biostudies-exchange' was not found in RabbitMQ. " +
                "Please ensure it is declared before the application starts.");
      }

      // Clear the session reference since the connection is broken
      stompSession = null;

      // Attempt to reconnect if not shutting down
      if (!isShuttingDown && rabbitMqConfig.getEnabled()) {
        scheduleReconnection();
      }
    }

    /**
     * Processes incoming STOMP message frames containing submission data.
     *
     * <p>Deserializes the payload as {@link JsonNode} and delegates to the submission sync listener
     * for business logic processing. Any errors during processing are logged but do not interrupt
     * the message flow.
     *
     * @param headers the STOMP message headers
     * @param payload the message payload (expected to be a JSON string)
     */
    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      String messageId = headers.getFirst(StompHeaders.MESSAGE_ID);
      log.debug("Received STOMP message - ID: {}", messageId);

      String jsonString = (String) payload;
      if (jsonString == null || jsonString.isBlank()) {
        log.warn("Received empty STOMP message payload - ID: {}", messageId);
        return;
      }

      // Check if this looks like JSON
      if (!jsonString.trim().startsWith("{") && !jsonString.trim().startsWith("[")) {
        log.error("Received non-JSON STOMP message payload - ID: {}, Content: {}", messageId, jsonString);
        return;
      }

      try {
        // Parse the JSON string payload
        JsonNode jsonMessage = objectMapper.readTree(jsonString);

        log.debug("Message to process: {}", jsonMessage);
        submissionSyncListener.processSubmissionUpdate(jsonMessage);
        log.debug("Successfully processed message: {}", messageId);

      } catch (Exception e) {
        log.error(
            "Failed to process RabbitMQ message with ID: {} - {}", messageId, e.getMessage());
      }
    }

    /**
     * Handles STOMP protocol-level exceptions.
     *
     * <p>If the session is disconnected, this method attempts to reconnect after a delay to avoid
     * overwhelming the broker. The reconnection is only attempted if the service is not shutting
     * down.
     *
     * @param session the STOMP session
     * @param command the STOMP command that triggered the exception
     * @param headers the STOMP headers
     * @param payload the raw payload bytes
     * @param exception the exception that occurred
     */
    @Override
    public void handleException(
        StompSession session,
        StompCommand command,
        StompHeaders headers,
        byte[] payload,
        Throwable exception) {

      log.error(
          "STOMP exception occurred - command: {}, error: {}", command, exception.getMessage());

      if (!session.isConnected()) {
        log.warn("Session disconnected due to exception");
        stompSession = null;

        // Attempt reconnection if not shutting down
        if (!isShuttingDown && rabbitMqConfig.getEnabled()) {
          scheduleReconnection();
        }
      }
    }
  }
}
