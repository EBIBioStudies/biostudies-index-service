package uk.ac.ebi.biostudies.index_service.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JsonPathServiceTest {

  private JsonPathReader jsonPathReader;
  private JsonPathService jsonPathService;
  private JsonNode submission;
  private PropertyDescriptor propertyDescriptor;

  @BeforeEach
  void setUp() {
    jsonPathReader = mock(JsonPathReader.class);
    jsonPathService = new JsonPathService(jsonPathReader);
    submission = mock(JsonNode.class);
    propertyDescriptor = mock(PropertyDescriptor.class);
  }

  @Test
  void testReadWithPropertyDescriptorAggregatesResults() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(Arrays.asList("$.a", "$.b"));

    when(jsonPathReader.read(submission, "$.a")).thenReturn(Arrays.asList("foo", "bar"));
    when(jsonPathReader.read(submission, "$.b")).thenReturn(Arrays.asList("baz", "foo"));

    List<String> results = jsonPathService.read(submission, propertyDescriptor);

    assertEquals(3, results.size());
    assertTrue(results.containsAll(Arrays.asList("foo", "bar", "baz")));
  }

  @Test
  void testReadWithPropertyDescriptorEmptyPathsReturnsEmptyList() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(Collections.emptyList());

    List<String> results = jsonPathService.read(submission, propertyDescriptor);

    assertTrue(results.isEmpty());
  }

  @Test
  void testReadWithSingleJsonPathExpressionReturnsResults() {
    when(jsonPathReader.read(submission, "$.singlePath")).thenReturn(Arrays.asList("val1", "val2"));

    List<String> results = jsonPathService.read(submission, "$.singlePath");

    assertEquals(2, results.size());
    assertTrue(results.containsAll(Arrays.asList("val1", "val2")));
  }

  @Test
  void testReadGracefullyHandlesJsonPathReaderException() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(Arrays.asList("$.valid", "$.fails"));

    when(jsonPathReader.read(submission, "$.valid")).thenReturn(Arrays.asList("validValue"));
    when(jsonPathReader.read(submission, "$.fails")).thenThrow(new IllegalArgumentException("bad path"));

    List<String> results = jsonPathService.read(submission, propertyDescriptor);

    // Should include values from the valid path, ignoring failure on the other
    assertEquals(1, results.size());
    assertTrue(results.contains("validValue"));
  }
}
