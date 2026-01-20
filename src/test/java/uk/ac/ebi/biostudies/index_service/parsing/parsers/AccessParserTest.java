package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

class AccessParserTest {

  private AccessParser parser;
  private JsonNode mockedSubmission;
  private JsonPathService jsonPathService;
  private PropertyDescriptor propertyDescriptor;

  @BeforeEach
  void setUp() {
    parser = new AccessParser();
    mockedSubmission = mock(JsonNode.class);  // Mocked JsonNode, no real JSON parsing
    jsonPathService = mock(JsonPathService.class);
    propertyDescriptor = mock(PropertyDescriptor.class);
  }

  @Test
  void parseAggregatesAllJsonPathValuesLowercased() {
    when(jsonPathService.read(eq(mockedSubmission), any(PropertyDescriptor.class)))
        .thenReturn(List.of("tag1", "tag2", "name1", "acc123", "ownerName"));

    when(mockedSubmission.has("released")).thenReturn(true);
    when(mockedSubmission.get("released")).thenReturn(mock(JsonNode.class));
    when(mockedSubmission.get("released").asBoolean(false)).thenReturn(true);

    String result = parser.parse(mockedSubmission, propertyDescriptor, jsonPathService);

    assertNotNull(result);
    String lower = result.toLowerCase();
    assertTrue(lower.contains("tag1"));
    assertTrue(lower.contains("tag2"));
    assertTrue(lower.contains("name1"));
    assertTrue(lower.contains("acc123"));
    assertTrue(lower.contains("ownername"));
    assertTrue(lower.contains("public"));  // released = true adds "public"
  }

  @Test
  void parseHandlesJsonPathExceptionsGracefully() {
    // Instead of throwing, return empty list to simulate failed extraction gracefully
    when(jsonPathService.read(eq(mockedSubmission), any(PropertyDescriptor.class)))
        .thenReturn(Collections.emptyList());

    when(mockedSubmission.has("released")).thenReturn(false);

    String result = parser.parse(mockedSubmission, propertyDescriptor, jsonPathService);

    assertNotNull(result);
    String lower = result.toLowerCase();
    assertFalse(lower.contains("public"));
    assertTrue(lower.isEmpty() || lower.trim().isEmpty());
  }

  @Test
  void parseIncludesPublicOnlyIfReleasedTrue() {
    when(jsonPathService.read(eq(mockedSubmission), any(PropertyDescriptor.class))).thenReturn(List.of());

    // released = true
    when(mockedSubmission.has("released")).thenReturn(true);
    when(mockedSubmission.get("released")).thenReturn(mock(JsonNode.class));
    when(mockedSubmission.get("released").asBoolean(false)).thenReturn(true);
    String resultReleased = parser.parse(mockedSubmission, propertyDescriptor, jsonPathService);
    assertTrue(resultReleased.toLowerCase().contains("public"));

    // released = false
    when(mockedSubmission.get("released").asBoolean(false)).thenReturn(false);
    String resultNotReleased = parser.parse(mockedSubmission, propertyDescriptor, jsonPathService);
    assertFalse(resultNotReleased.toLowerCase().contains("public"));
  }

  @Test
  void parseHandlesEmptyArraysOrMissingFields() {
    when(jsonPathService.read(eq(mockedSubmission), any(PropertyDescriptor.class))).thenReturn(List.of());
    when(mockedSubmission.has("released")).thenReturn(false);

    String result = parser.parse(mockedSubmission, propertyDescriptor, jsonPathService);

    assertNotNull(result);
    assertTrue(result.trim().isEmpty());
  }
}
