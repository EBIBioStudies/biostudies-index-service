package uk.ac.ebi.biostudies.index_service.messages;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for RabbitMQStompService using Testcontainers.
 * Requires Docker to be running - tests are skipped if Docker is not available.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "messaging.stomp.enabled=true",
    "rabbitmq.stomp.loginHeader=guest",
    "rabbitmq.stomp.passwordHeader=guest"
})
class RabbitMQStompServiceIT {

  // Removed the static block and manually start container logic.
  // Testcontainers will now read ryuk.disabled=true from ~/.testcontainers.properties 
  // at the very first moment the library is touched.

  static GenericContainer<?> rabbitmq =
      new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management"))
          .withExposedPorts(5672, 15672, 15674, 61613)
          .withEnv("RABBITMQ_DEFAULT_USER", "guest")
          .withEnv("RABBITMQ_DEFAULT_PASS", "guest")
          .withCommand(
              "bash",
              "-c",
              "rabbitmq-plugins enable rabbitmq_web_stomp rabbitmq_stomp && rabbitmq-server")
          .waitingFor(
              Wait.forLogMessage(".*Server startup complete.*\\n", 1)
                  .withStartupTimeout(Duration.ofSeconds(120)));

  @Autowired private RabbitMQStompService stompService;

  @BeforeAll
  static void startContainer() {
    if (!rabbitmq.isRunning()) {
      rabbitmq.start();
    }
  }

  @BeforeAll
  static void checkDockerAvailable() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available - skipping integration tests");
  }

  @BeforeAll
  static void setupRabbitMQ() throws Exception {
    String host = getRoutableHost(rabbitmq.getHost(), rabbitmq.getMappedPort(5672));
    
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setPort(rabbitmq.getMappedPort(5672));
    factory.setUsername("guest");
    factory.setPassword("guest");

    try (Connection connection = factory.newConnection();
        Channel channel = connection.createChannel()) {
      channel.exchangeDeclare("biostudies-exchange", "topic", true);
      String queueName = "test-queue";
      channel.queueDeclare(queueName, true, false, false, null);
      channel.queueBind(queueName, "biostudies-exchange", "bio.submission.#");
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("rabbitmq.stomp.host", () -> getRoutableHost(rabbitmq.getHost(), rabbitmq.getMappedPort(15674)));
    registry.add("rabbitmq.stomp.port", () -> rabbitmq.getMappedPort(15674));
    registry.add("biostudies.rabbitmq.partial.submission.queue", () -> "test-queue");
    registry.add("biostudies.rabbitmq.exchange.name", () -> "biostudies-exchange");
    registry.add("biostudies.rabbitmq.routing-key.published", () -> "bio.submission.published");
    registry.add("biostudies.rabbitmq.routing-key.partials", () -> "bio.submission.partials");
  }

  private static String getRoutableHost(String host, int port) {
    try (Socket s = new Socket()) {
      s.connect(new InetSocketAddress(host, port), 2000);
      return host;
    } catch (Exception e) {
      return "127.0.0.1";
    }
  }

  @BeforeEach
  void setUp() {
    stompService.startWebSocket();
  }

  @AfterEach
  void tearDown() {
    stompService.stopWebSocket();
  }

  @Test
  void testConnectionEstablishment() {
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> {
          if (!stompService.isSessionConnected()) stompService.startWebSocket();
          return stompService.isSessionConnected();
        });
    assertTrue(stompService.isSessionConnected());
  }

  @Test
  void testReconnectionAfterDisconnect() {
    await().atMost(Duration.ofSeconds(30)).until(stompService::isSessionConnected);
    stompService.stopWebSocket();
    assertFalse(stompService.isSessionConnected());
    stompService.startWebSocket();
    await().atMost(Duration.ofSeconds(30)).until(stompService::isSessionConnected);
  }
}
