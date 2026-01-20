package uk.ac.ebi.biostudies.index_service.parsing;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JaywayJsonPathReaderTest {

  private JaywayJsonPathReader jsonPathService;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    jsonPathService = new JaywayJsonPathReader();
    mapper = new ObjectMapper();
  }

  @Test
  void read_validJsonPath_returnsValues() throws Exception {
    String json = "{ \"section\": { \"attributes\": [ { \"name\": \"Title\", \"value\": \"Example\" } ] } }";
    JsonNode node = mapper.readTree(json);

    List<String> results = jsonPathService.read(node, "$.section.attributes[?(@.name =~ /Title/i)].value");

    assertEquals(1, results.size());
    assertEquals("Example", results.get(0));
  }

  @Test
  void read_jsonPathNoMatches_returnsEmptyList() throws Exception {
    String json = "{ \"section\": { \"attributes\": [] } }";
    JsonNode node = mapper.readTree(json);

    List<String> results = jsonPathService.read(node, "$.section.attributes[?(@.name =~ /Nonexistent/i)].value");

    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  void read_invalidJsonPath_throwsRuntimeException() throws Exception {
    String json = "{ \"foo\": \"bar\" }";
    JsonNode node = mapper.readTree(json);

    assertThrows(RuntimeException.class, () ->
        jsonPathService.read(node, "$..[invalid$$jsonpath]")
    );
  }

  @Test
  void read_nullJsonNode_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () ->
        jsonPathService.read(null, "$.some.path")
    );
  }

  @Test
  void read_nullJsonPathExpression_throwsIllegalArgumentException() throws Exception {
    String json = "{}";
    JsonNode node = mapper.readTree(json);

    assertThrows(IllegalArgumentException.class, () ->
        jsonPathService.read(node, null)
    );
  }

  @Test
  void read_emptyJsonPathExpression_throwsIllegalArgumentException() throws Exception {
    String json = "{}";
    JsonNode node = mapper.readTree(json);

    assertThrows(IllegalArgumentException.class, () ->
        jsonPathService.read(node, "")
    );
  }


  // Additional tests for new scenarios

  @Test
  void read_singleValueReturnsList() throws Exception {
    String json = "{ \"owner\": \"John\" }";
    JsonNode node = mapper.readTree(json);

    List<String> results = jsonPathService.read(node, "$.owner");

    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals("John", results.get(0));
  }

  @Test
  void read_nullResultReturnsEmptyList() throws Exception {
    String json = "{ \"data\": null }";
    JsonNode node = mapper.readTree(json);

    // Querying a null value should return empty list safely
    List<String> results = jsonPathService.read(node, "$.data");

    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  void read_nonArrayResultReturnsSingleElementList() throws Exception {
    String json = "{ \"value\": 42 }";
    JsonNode node = mapper.readTree(json);

    List<String> results = jsonPathService.read(node, "$.value");

    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals("42", results.get(0));
  }

  @Test
  void read_arrayResultReturnsAllElementsAsStrings() throws Exception {
    String json = "{ \"numbers\": [1, 2, 3] }";
    JsonNode node = mapper.readTree(json);

    List<String> results = jsonPathService.read(node, "$.numbers");

    assertNotNull(results);
    assertEquals(3, results.size());
    assertEquals("1", results.get(0));
    assertEquals("2", results.get(1));
    assertEquals("3", results.get(2));
  }

}
