package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

class JPathListParserTest {

  private JPathListParser parser;
  private JsonPathService jsonPathService;
  private PropertyDescriptor propertyDescriptor;
  private JsonNode submission;

  @BeforeEach
  void setUp() {
    parser = new JPathListParser();
    jsonPathService = Mockito.mock(JsonPathService.class);
    propertyDescriptor = Mockito.mock(PropertyDescriptor.class);
    submission = Mockito.mock(JsonNode.class);
  }

  @Test
  void testSingleJsonPathArrayResult() {
    // Setup PropertyDescriptor configuration for a single JSONPath returning an array
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.authors"));
    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.FACET);

    // Mock the jsonPathService to return an array list of elements for the JSONPath
    when(jsonPathService.read(submission, propertyDescriptor))
        .thenReturn(List.of("Alice", "Bob", "Charlie"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("Alice|Bob|Charlie", result);
  }

  @Test
  void testMultipleJsonPathsOrLogic() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.authors", "$.contributors"));
    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.TOKENIZED_STRING);

    when(jsonPathService.read(submission, propertyDescriptor)).thenReturn(List.of("Alice", "Bob", "Charlie"));

    // Should combine results from both paths
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("Alice Bob Charlie", result);
  }

  @Test
  void testFacetFieldTypeJoinsWithDelimiter() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.tags"));
    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.FACET);

    when(jsonPathService.read(submission, propertyDescriptor)).thenReturn(List.of("tag1", "tag2"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("tag1|tag2", result);
  }

  @Test
  void testLongFieldTypeSum() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.counts"));
    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.LONG);

    when(jsonPathService.read(submission, propertyDescriptor)).thenReturn(List.of("10", "20", "5"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("35", result);  // Sum 10+20+5=35
  }

  @Test
  void testToLowerCaseFlagApplied() {
    when(propertyDescriptor.getJsonPaths()).thenReturn(List.of("$.names"));
    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.TOKENIZED_STRING);
    when(propertyDescriptor.getToLowerCase()).thenReturn(true);

    when(jsonPathService.read(submission, propertyDescriptor)).thenReturn(List.of("Alice", "BOB"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("alice bob", result);
  }

}
