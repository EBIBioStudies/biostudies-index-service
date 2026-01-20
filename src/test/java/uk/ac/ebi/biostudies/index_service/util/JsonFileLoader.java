package uk.ac.ebi.biostudies.index_service.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JsonFileLoader {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Loads a JSON file from test resources and parses it into a JsonNode.
   *
   * @param resourcePath path relative to src/test/resources
   * @return parsed JsonNode
   * @throws IOException if file cannot be read or parsed
   * @throws AssertionError if file is not found
   */
  public static JsonNode loadJsonFromResources(String resourcePath) throws IOException {
    InputStream inputStream = JsonFileLoader.class.getClassLoader().getResourceAsStream(resourcePath);

    assertNotNull(inputStream, "Test file not found: " + resourcePath);

    try {
      return objectMapper.readTree(inputStream);
    } finally {
      inputStream.close();
    }
  }

  /**
   * Loads expected output map from test resources and parses it into a Map.
   *
   * @param resourcePath path relative to src/test/resources
   * @return parsed Map<String, Object>
   * @throws IOException if file cannot be read or parsed
   * @throws AssertionError if file is not found
   */
  public static Map<String, Object> loadExpectedMapFromResources(String resourcePath) throws IOException {
    InputStream inputStream = JsonFileLoader.class.getClassLoader().getResourceAsStream(resourcePath);

    assertNotNull(inputStream, "Expected output file not found: " + resourcePath);

    try {
      return objectMapper.readValue(inputStream, new TypeReference<>() {});
    } finally {
      inputStream.close();
    }
  }

  public static List<Map<String, Object>> loadExpectedListOfMapsFromResources(String resourcePath) throws IOException {
    InputStream inputStream = JsonFileLoader.class.getClassLoader().getResourceAsStream(resourcePath);

    assertNotNull(inputStream, "Expected output file not found: " + resourcePath);

    try {
      return objectMapper.readValue(inputStream, new TypeReference<>() {});
    } finally {
      inputStream.close();
    }
  }

  /**
   * Converts a Map to JsonNode using the shared ObjectMapper instance.
   *
   * @param map the map to convert
   * @return JsonNode representation of the map
   * @throws IllegalArgumentException if map contains non-serializable values
   */
  public static JsonNode toJsonNode(Map<String, Object> map) {
    Objects.requireNonNull(map, "map must not be null");
    return objectMapper.valueToTree(map);
  }
}