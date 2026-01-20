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

class TypeParserTest {

  private TypeParser parser;
  private JsonPathService jsonPathService;
  private PropertyDescriptor propertyDescriptor;
  private JsonNode submission;

  @BeforeEach
  void setUp() {
    parser = new TypeParser();
    jsonPathService = Mockito.mock(JsonPathService.class);
    propertyDescriptor = Mockito.mock(PropertyDescriptor.class);
    submission = Mockito.mock(JsonNode.class);
  }

  @Test
  void testParseReturnsLowercaseType() {
    when(jsonPathService.read(submission, "$.section.type")).thenReturn(List.of("STUDY"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("study", result);
  }

  @Test
  void testParseReturnsCollectionWhenTypeIsProject() {
    when(jsonPathService.read(submission, "$.section.type")).thenReturn(List.of("project"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("collection", result); // expects substitution of 'project' to 'collection'
  }

  @Test
  void testParseReturnsEmptyStringWhenNoTypeFound() {
    when(jsonPathService.read(submission, "$.section.type")).thenReturn(List.of());

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("", result);
  }

  @Test
  void testParseReturnsEmptyStringWhenNullReturned() {
    when(jsonPathService.read(submission, "$.section.type")).thenReturn(null);

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("", result);
  }

  @Test
  void testParseThrowsIllegalArgumentWhenJsonPathServiceThrows() {
    when(jsonPathService.read(submission, "$.section.type")).thenThrow(new IllegalArgumentException("fail"));

    assertThrows(IllegalArgumentException.class, () ->
        parser.parse(submission, propertyDescriptor, jsonPathService));
  }
}