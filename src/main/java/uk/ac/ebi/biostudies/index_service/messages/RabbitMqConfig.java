package uk.ac.ebi.biostudies.index_service.messages;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for RabbitMQ STOMP (Simple Text Oriented Messaging Protocol) over
 * WebSocket.
 *
 * <p>This configuration manages the connection settings and credentials for STOMP messaging
 * integration with RabbitMQ. STOMP support is currently disabled by default and can be activated by
 * setting the {@code biostudies.rabbitmq.stomp.enabled} property to {@code true}.
 *
 * @see org.springframework.messaging.simp.stomp.StompSession
 */
@Getter
@Component
public class RabbitMqConfig {

  /**
   * The header name used for transmitting the login/username in STOMP connections.
   * Defaults to empty string if not specified.
   */
  private final String loginHeader;

  /**
   * The header name used for transmitting the password in STOMP connections.
   * Defaults to empty string if not specified.
   */
  private final String passwordHeader;

  /**
   * The hostname or IP address of the RabbitMQ server for STOMP connections.
   * Defaults to empty string if not specified.
   */
  private final String host;

  /**
   * The port number on which the RabbitMQ STOMP broker is listening.
   * Typically 61613 for STOMP or 15674 for STOMP over WebSocket.
   * Defaults to null if not specified.
   */
  private final Integer port;

  /**
   * Flag indicating whether STOMP messaging over WebSocket is enabled.
   * When {@code false}, STOMP/WebSocket functionality is disabled and the other
   * configuration properties are not used.
   * Defaults to {@code false}.
   */
  private final Boolean enabled;

  /**
   * Constructor for RabbitMqConfig with all STOMP configuration values injected via @Value annotations.
   *
   * @param loginHeader header name for login/username
   * @param passwordHeader header name for password
   * @param host RabbitMQ server hostname or IP
   * @param port RabbitMQ STOMP port number
   * @param enabled flag to enable/disable STOMP messaging
   */
  public RabbitMqConfig(
      @Value("${rabbitmq.stomp.loginHeader:}") String loginHeader,
      @Value("${rabbitmq.stomp.passwordHeader:}") String passwordHeader,
      @Value("${rabbitmq.stomp.host:}") String host,
      @Value("${rabbitmq.stomp.port:#{null}}") Integer port,
      @Value("${messaging.stomp.enabled:false}") Boolean enabled) {
    this.loginHeader = loginHeader;
    this.passwordHeader = passwordHeader;
    this.host = host;
    this.port = port;
    this.enabled = enabled;
  }
}
