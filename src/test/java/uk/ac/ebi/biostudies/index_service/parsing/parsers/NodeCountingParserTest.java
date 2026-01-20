package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

class NodeCountingParserTest {

  private NodeCountingParser parser;
  private JsonPathService jsonPathService;
  private PropertyDescriptor propertyDescriptor;
  private JsonNode submission;

  @BeforeEach
  void setUp() {
    parser = new NodeCountingParser();
    jsonPathService = Mockito.mock(JsonPathService.class);
    propertyDescriptor = Mockito.mock(PropertyDescriptor.class);
    submission = Mockito.mock(JsonNode.class);
  }

  @Test
  void testSingleJsonPathWithMultipleResults() {
    // Given
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.items"));
    when(jsonPathService.read(submission, propertyDescriptor)).thenReturn(List.of("1", "2", "3", "4"));

    // When
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    // Then
    assertEquals("4", result);
  }

  @Test
  void testSingleJsonPathSingleValueResult() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.value"));
    when(jsonPathService.read(submission, propertyDescriptor)).thenReturn(List.of("onlyOne"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("1", result);
  }

  @Test
  void testMultipleJsonPathsAdditiveLogic() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.first", "$.second"));
    when(jsonPathService.read(submission, propertyDescriptor)).thenReturn(List.of("A", "B", "C", "D", "E"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("5", result); // total 2 + 3
  }

  @Test
  void testEmptyListResultsInZero() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.empty"));
    when(jsonPathService.read(submission, propertyDescriptor)).thenReturn(List.of());

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("0", result);
  }

  @Test
  void testThrowsIllegalArgumentOnCriticalError() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.crash"));
    when(jsonPathService.read(submission, propertyDescriptor))
        .thenThrow(new IllegalStateException("severe issue"));

    assertThrows(
        IllegalStateException.class,
        () -> parser.parse(submission, propertyDescriptor, jsonPathService));
  }
}
