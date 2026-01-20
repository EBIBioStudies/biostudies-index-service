package uk.ac.ebi.biostudies.index_service.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.client.MockServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.utility.DockerImageName;
import uk.ac.ebi.biostudies.index_service.client.PaginatedExtSubmissionHttpClient;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionIndexControllerTest {

  @TempDir
  static java.nio.file.Path tempDir;

  // Removed @Testcontainers and @Container to prevent the aggressive Ryuk check.
  // We will start the container manually in a @BeforeAll block.
  static MockServerContainer mockServerContainer =
      new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"));

  static MockServerClient mockServerClient;

  @BeforeAll
  static void startContainer() {
    if (!mockServerContainer.isRunning()) {
      mockServerContainer.start();
    }
  }

  @Autowired
  PaginatedExtSubmissionHttpClient extClient;
  @Autowired SecurityConfig securityConfig;
  @LocalServerPort private Integer port;

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("index.base-dir", () -> tempDir.toAbsolutePath().toString());

    String host = getRoutableHost(mockServerContainer.getHost(), mockServerContainer.getServerPort());
    mockServerClient = new MockServerClient(host, mockServerContainer.getServerPort());
    
    registry.add("backend.baseUrl", () -> 
        String.format("http://%s:%d", host, mockServerContainer.getServerPort()));
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
    RestAssured.port = port;
    mockServerClient.reset();
  }

  @Test
  @Order(1)
  void shouldQueueSubmissionForIndexing() {
    String accNo = "S-BSST123";

    // Mock external submission fetch (called async, but test verifies enqueue response)
    mockServerClient
        .when(
            request()
                .withMethod("GET")
                .withPath("/submissions/extended/" + accNo)
                .withHeader(header("X-Session-Token", securityConfig.getPartialUpdateRestToken())))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonSampleForExtendedSubmissionMetadata()));

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/submissions/{accNo}/index", accNo)
        .then()
        .statusCode(HttpStatus.SC_ACCEPTED)  // 202
        .contentType(ContentType.JSON)
        .body("success", is(true))
        .body("message", is("Submission S-BSST123 queued (position: 1)"))
        .body("data.accNo", is(accNo))
        .body("data.queuePosition", is(1))  // Expect position 1 (empty queue)
        .body("data.taskId", isA(String.class))  // UUID present
        .body("data.statusUrl", is("/submissions/S-BSST123/status"))
        .body("errors", is(List.of()));
  }


  @Test
  void shouldReturnNotFoundStatusForNonexistentTask() {
    given()
        .when().get("/submissions/{accNo}/status", "S-NONEXISTENT")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("success", is(true))
        .body("data.state", is("NOT_FOUND"))
        .body("data.message", containsString("No active indexing task for S-NONEXISTENT"))
        .body("data.taskId", is(nullValue()));  // No taskId for ghosts
  }

  @Test
  void shouldListEmptyActiveTasks() {
    given()
        .when().get("/submissions/tasks")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("success", is(true))
        .body("message", is("Found 0 active indexing tasks"))
        .body("data", is(List.of()));
  }

  private String jsonSampleForExtendedSubmissionMetadata() {
    return """
        {
          "accNo": "S-BSST123",
          "releaseTime": "2026-01-13T16:00:00Z"
        }
        """;
  }
}
