package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

public class EUToxRiskDataTypeParserTest {

  private EUToxRiskDataTypeParser parser;
  private PropertyDescriptor propertyDescriptor;
  private JsonPathService jsonPathService;
  private JsonNode submission;

  @BeforeEach
  public void setUp() {
    parser = new EUToxRiskDataTypeParser();
    propertyDescriptor = mock(PropertyDescriptor.class);
    jsonPathService = mock(JsonPathService.class);
    submission = mock(JsonNode.class);
  }

  @Test
  public void testParse_WithQMRFID_ReturnsInSilico() {
    // Setup JSON path query to find QMRF-ID attribute returning non-empty list
    when(jsonPathService.read(eq(submission), anyString()))
        .thenReturn(java.util.List.of("someValue"));

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("in silico", result);
  }

  @Test
  public void testParse_WithoutQMRFID_ReturnsInVitro() {
    // Setup JSON path query to find QMRF-ID attribute returning empty list
    when(jsonPathService.read(eq(submission), anyString()))
        .thenReturn(java.util.Collections.emptyList());

    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("in vitro", result);
  }
}
