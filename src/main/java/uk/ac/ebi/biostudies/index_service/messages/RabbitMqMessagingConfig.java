package uk.ac.ebi.biostudies.index_service.messages;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for RabbitMQ messaging queues, exchanges, and routing keys.
 *
 * <p>This configuration defines the application-level messaging structure including queue names,
 * exchange names, and routing keys used for BioStudies submission processing. These are operational
 * settings that define how the application interacts with RabbitMQ.
 */
@Getter
@Component
public class RabbitMqMessagingConfig {

  /**
   * The base name of the queue for receiving partial and published submission messages. The actual
   * queue name used at runtime will have the hostname appended (e.g.,
   * "submission-submitted-partials-queue-hostname") to ensure uniqueness across multiple service
   * instances.
   */
  private final String partialSubmissionQueue;

  /**
   * The name of the RabbitMQ exchange for BioStudies messages. All submission-related routing keys
   * are bound to this exchange.
   */
  private final String exchangeName;

  /**
   * Routing key for published submission messages. Used to route fully published submissions
   * through the exchange.
   */
  private final String publishedRoutingKey;

  /**
   * Routing key for partial submission messages. Used to route partial/draft submissions through
   * the exchange.
   */
  private final String partialsRoutingKey;

  /**
   * Constructor for RabbitMqMessagingConfig with all messaging configuration values injected
   * via @Value annotations.
   *
   * @param partialSubmissionQueue base queue name for partial submissions
   * @param exchangeName RabbitMQ exchange name
   * @param publishedRoutingKey routing key for published submissions
   * @param partialsRoutingKey routing key for partial submissions
   */
  public RabbitMqMessagingConfig(
      @Value("${biostudies.rabbitmq.partial.submission.queue:submission-submitted-partials-queue}")
          String partialSubmissionQueue,
      @Value("${biostudies.rabbitmq.exchange.name:biostudies-exchange}") String exchangeName,
      @Value("${biostudies.rabbitmq.routing-key.published:bio.submission.published}")
          String publishedRoutingKey,
      @Value("${biostudies.rabbitmq.routing-key.partials:bio.submission.partials}")
          String partialsRoutingKey) {
    this.partialSubmissionQueue = partialSubmissionQueue;
    this.exchangeName = exchangeName;
    this.publishedRoutingKey = publishedRoutingKey;
    this.partialsRoutingKey = partialsRoutingKey;
  }
}
