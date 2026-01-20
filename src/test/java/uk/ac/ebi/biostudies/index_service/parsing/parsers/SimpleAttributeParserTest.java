package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

class SimpleAttributeParserTest {

  private SimpleAttributeParser parser;
  private JsonPathService jsonPathService;
  private PropertyDescriptor propertyDescriptor;
  private JsonNode submission;

  @BeforeEach
  void setUp() {
    parser = new SimpleAttributeParser();
    jsonPathService = Mockito.mock(JsonPathService.class);
    propertyDescriptor = Mockito.mock(PropertyDescriptor.class);
    submission = Mockito.mock(JsonNode.class);
  }

  @Test
  void parse_singleFacetValue_returnsValue() {
    String title = "Project/Work";

    when(propertyDescriptor.getTitle()).thenReturn(title);
    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.FACET);
    String expression = parser.buildJsonPathExpression(title);

    when(jsonPathService.read(submission, expression)).thenReturn(List.of("value1"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("value1", result);
    // Adding this to test that the title is properly formatted when it contains "/".
    // No need to assert for this in other tests
    assertEquals("$.section.attributes[?(@.name=~ /Project\\/Work/i)].value", expression);
  }

  @Test
  void parse_multipleFacetValues_joinsWithDelimiter() throws Exception {

    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.FACET);
    when(jsonPathService.read(eq(submission), anyString())).thenReturn(List.of("value1", "value2"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    String expected = String.join("|", List.of("value1", "value2"));

    assertEquals(expected, result);
  }

  @Test
  void parse_withMatchRegex_extractsGroup() {

    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.FACET);
    when(propertyDescriptor.hasMatch()).thenReturn(true);
    when(propertyDescriptor.getMatch()).thenReturn("prefix-(\\d+)");
    when(jsonPathService.read(eq(submission), anyString()))
        .thenReturn(List.of("prefix-123", "prefix-456"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("123|456", result);
  }

  @Test
  void parse_withInvalidMatchRegex_throwsPatternSyntaxException() {

    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.FACET);
    when(propertyDescriptor.hasMatch()).thenReturn(true);
    when(propertyDescriptor.getMatch()).thenReturn("[\\]'");

    when(jsonPathService.read(eq(submission), anyString()))
        .thenReturn(List.of("prefix-123", "prefix-456"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              parser.parse(submission, propertyDescriptor, jsonPathService);
            });

    String expectedMessage = "Regular expression syntax error: [\\]'";
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  void parse_fieldTypeLong_singleValue() throws Exception {

    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.LONG);
    when(jsonPathService.read(eq(submission), anyString())).thenReturn(List.of("42"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("42", result);
  }

  @Test
  void parse_invalid_fieldTypeLong_singleValue() {

    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.LONG);
    when(jsonPathService.read(eq(submission), anyString())).thenReturn(List.of("xyz"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    // We don't expect an exception because a value in the submission that was expected to be
    // a number is not. We just don't process it
    assertEquals("", result);
  }

  @Test
  void parse_fieldTypeLong_multipleValues() throws Exception {

    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.LONG);
    when(jsonPathService.read(eq(submission), anyString())).thenReturn(List.of("5", "10"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("2", result); // count of values as string
  }

  @Test
  void parse_defaultFieldType_joinsMultiple() throws Exception {

    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.TOKENIZED_STRING);
    when(jsonPathService.read(eq(submission), anyString())).thenReturn(List.of("desc1", "desc2"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("desc1 desc2", result);
  }

  @Test
  void parse_noMatchingAttributes_returnsEmpty() throws Exception {

    when(propertyDescriptor.getFieldType()).thenReturn(FieldType.FACET);

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("", result);
  }
}
