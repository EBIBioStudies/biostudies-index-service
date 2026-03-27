package uk.ac.ebi.biostudies.index_service.messages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import uk.ac.ebi.biostudies.index_service.config.AppRoleConfig;
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

  // --- Reconnection/backoff config ---
  private static final long INITIAL_RECONNECTION_DELAY_SECONDS = 10;
  private static final long MAX_RECONNECTION_DELAY_SECONDS = 300; // 5 minutes

  private final RabbitMqConfig rabbitMqConfig;
  private final RabbitMqMessagingConfig messagingConfig;
  private final SubmissionSyncListener submissionSyncListener;
  private final AppRoleConfig appRoleConfig;
  private final ObjectMapper objectMapper;

  private final AtomicReference<StompSession> stompSession = new AtomicReference<>();
  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

  // NEW: reconnection coordination
  private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
  private final AtomicReference<Long> currentBackoffSeconds =
      new AtomicReference<>(INITIAL_RECONNECTION_DELAY_SECONDS);

  private ThreadPoolTaskScheduler taskScheduler;
  private WebSocketStompClient stompClient; // reuse single client

  public RabbitMQStompService(
      RabbitMqConfig rabbitMqConfig,
      RabbitMqMessagingConfig messagingConfig,
      SubmissionSyncListener submissionSyncListener,
      AppRoleConfig appRoleConfig) {
    this.rabbitMqConfig = rabbitMqConfig;
    this.messagingConfig = messagingConfig;
    this.submissionSyncListener = submissionSyncListener;
    this.appRoleConfig = appRoleConfig;
    this.objectMapper = new ObjectMapper();
  }

  public boolean isSessionConnected() {
    StompSession session = stompSession.get();
    return session != null && session.isConnected();
  }

  public void stopWebSocket() {
    StompSession session = stompSession.getAndSet(null);
    if (session != null && session.isConnected()) {
      try {
        session.disconnect();
        log.info("STOMP session disconnected");
      } catch (Exception e) {
        log.warn("Error while disconnecting STOMP session: {}", e.getMessage());
      }
    }

    // Reset reconnect bookkeeping so an explicit stop/start cycle behaves like a fresh connect.
    isReconnecting.set(false);
    currentBackoffSeconds.set(INITIAL_RECONNECTION_DELAY_SECONDS);
  }

  public void startWebSocket() {
    if (!isSessionConnected()) {
      // Explicit start should be immediate and deterministic.
      isReconnecting.set(false);
      currentBackoffSeconds.set(INITIAL_RECONNECTION_DELAY_SECONDS);
      connectNow();
    }
  }

  public synchronized void init() {
    log.debug("Initiating STOMP client service");
    connectNow();
  }

  private void connectNow() {
    if (!rabbitMqConfig.isEnabled()) {
      log.debug("STOMP client is disabled");
      return;
    }

    if (!appRoleConfig.isWriterRole()) {
      log.debug("Application not configured with writer role");
      return;
    }

    if (isShuttingDown.get()) {
      log.debug("Service is shutting down, skipping connection attempt");
      return;
    }

    if (isSessionConnected()) {
      log.debug("STOMP session already connected, skipping init");
      return;
    }

    String host = rabbitMqConfig.getHost();
    Integer port = rabbitMqConfig.getPort();

    if (host == null || host.isBlank() || port == null) {
      log.error(
          "STOMP connection failed: host or port is not configured properly. host: '{}', port: '{}'",
          host,
          port);
      return;
    }

    String url = String.format("ws://%s:%s/ws", host, port);
    log.info("Connecting to STOMP broker at: {}", url);

    // Initialize scheduler once
    if (taskScheduler == null) {
      taskScheduler = new ThreadPoolTaskScheduler();
      taskScheduler.setPoolSize(2);
      taskScheduler.setThreadNamePrefix("stomp-scheduler-");
      taskScheduler.initialize();
    }

    // Initialize STOMP client once
    if (stompClient == null) {
      WebSocketClient client = new StandardWebSocketClient();
      stompClient = new WebSocketStompClient(client);
      stompClient.setMessageConverter(new StringMessageConverter());
      stompClient.setTaskScheduler(taskScheduler);
    }

    StompHeaders stompHeaders = new StompHeaders();
    stompHeaders.setLogin(rabbitMqConfig.getLoginHeader());
    stompHeaders.setPasscode(rabbitMqConfig.getPasswordHeader());
    stompHeaders.setAcceptVersion("1.1", "1.2");

    RabbitMQStompSessionHandler sessionHandler = new RabbitMQStompSessionHandler();

    CompletableFuture<StompSession> sessionFuture =
        stompClient.connectAsync(url, new WebSocketHttpHeaders(), stompHeaders, sessionHandler);

    sessionFuture
        .thenAccept(
            session -> {
              stompSession.set(session);
              // Successful connection: reset backoff and reconnection flag
              currentBackoffSeconds.set(INITIAL_RECONNECTION_DELAY_SECONDS);
              isReconnecting.set(false);
              log.info("STOMP session established: {}", session.getSessionId());
            })
        .exceptionally(
            ex -> {
              log.error("Failed to connect to STOMP broker at {}: {}", url, ex.getMessage());
              stompSession.set(null);
              if (!isShuttingDown.get() && rabbitMqConfig.isEnabled()) {
                scheduleReconnection();
              }
              return null;
            });

    log.debug("STOMP client connection initiated to {}", url);
  }

  private void scheduleReconnection() {
    // Ensure only one reconnection loop at a time
    if (!isReconnecting.compareAndSet(false, true)) {
      log.debug("Reconnection already scheduled or in progress, skipping");
      return;
    }

    long delay = currentBackoffSeconds.get();
    log.info("Scheduling STOMP reconnection attempt in {} seconds...", delay);

    CompletableFuture.runAsync(
        () -> {
          try {
            TimeUnit.SECONDS.sleep(delay);
            if (!isShuttingDown.get() && rabbitMqConfig.isEnabled()) {
              log.info("Attempting to reconnect to STOMP broker...");
              connectNow();
              // Note: connectNow() will clear isReconnecting on success or
              // schedule another reconnection on failure.
            } else {
              isReconnecting.set(false);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Reconnection attempt interrupted");
            isReconnecting.set(false);
          } finally {
            // Increase backoff for next failure (bounded)
            currentBackoffSeconds.updateAndGet(
                oldDelay -> Math.min(oldDelay * 2, MAX_RECONNECTION_DELAY_SECONDS));
          }
        });
  }

  @PreDestroy
  public void destroy() {
    log.info("Shutting down RabbitMQ STOMP service");
    isShuttingDown.set(true);

    stopWebSocket();

    if (taskScheduler != null) {
      taskScheduler.shutdown();
      log.info("Task scheduler shut down");
    }
  }

  // --- inner handler unchanged, except that all reconnection triggers now go through
  // scheduleReconnection() ---

  protected class RabbitMQStompSessionHandler extends StompSessionHandlerAdapter {

    @Override
    public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
      return String.class;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
      String submissionPartialQueue = messagingConfig.getPartialSubmissionQueue();
      try {
        String hostname = InetAddress.getLocalHost().getHostName();
        submissionPartialQueue = submissionPartialQueue + "-" + hostname;
      } catch (UnknownHostException e) {
        log.error("Failed to get hostname, using queue name without hostname suffix", e);
      }

      stompSession.compareAndSet(null, session);

      log.info(
          "STOMP connection established - session: {}, server: {}",
          connectedHeaders.getFirst("session"),
          connectedHeaders.getFirst("server"));

      subscribeToRoutingKey(submissionPartialQueue, messagingConfig.getPublishedRoutingKey());
      subscribeToRoutingKey(submissionPartialQueue, messagingConfig.getPartialsRoutingKey());

      log.info("STOMP client subscribed to queue: {}", submissionPartialQueue);
    }

    private void subscribeToRoutingKey(String queueName, String routingKey) {
      StompHeaders headers = new StompHeaders();
      headers.setDestination("/exchange/" + messagingConfig.getExchangeName() + "/" + routingKey);
      headers.set("x-queue-name", queueName);
      headers.set("auto-delete", "false");
      headers.set("durable", "true");

      StompSession session = stompSession.get();
      if (session != null && session.isConnected()) {
        session.subscribe(headers, this);
        log.debug("Subscribed to routing key: {} on queue: {}", routingKey, queueName);
      } else {
        log.warn(
            "Cannot subscribe to routing key {} on queue {} because session is not connected",
            routingKey,
            queueName);
      }
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      log.error("STOMP transport error occurred: {}", exception.getMessage());

      if (exception.getMessage() != null
          && exception.getMessage().contains("not_found")
          && exception.getMessage().contains("biostudies-exchange")) {
        log.error(
            "CRITICAL: The required exchange 'biostudies-exchange' was not found in RabbitMQ. "
                + "Please ensure it is declared before the application starts.");
      }

      stompSession.set(null);

      if (!isShuttingDown.get() && rabbitMqConfig.isEnabled()) {
        scheduleReconnection();
      }
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      String messageId = headers.getFirst(StompHeaders.MESSAGE_ID);
      log.debug("Received STOMP message - ID: {}", messageId);

      String jsonString = (String) payload;
      if (jsonString == null || jsonString.isBlank()) {
        log.warn("Received empty STOMP message payload - ID: {}", messageId);
        return;
      }

      if (!jsonString.trim().startsWith("{") && !jsonString.trim().startsWith("[")) {
        log.error(
            "Received non-JSON STOMP message payload - ID: {}, Content: {}", messageId, jsonString);
        return;
      }

      try {
        JsonNode jsonMessage = objectMapper.readTree(jsonString);
        log.debug("Message to process: {}", jsonMessage);
        submissionSyncListener.processSubmissionUpdate(jsonMessage);
        log.debug("Successfully processed message: {}", messageId);
      } catch (Exception e) {
        log.error("Failed to process RabbitMQ message with ID: {} - {}", messageId, e.getMessage());
      }
    }

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
        stompSession.set(null);

        if (!isShuttingDown.get() && rabbitMqConfig.isEnabled()) {
          scheduleReconnection();
        }
      }
    }
  }
}
