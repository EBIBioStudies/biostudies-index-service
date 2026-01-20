package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.util.SubmissionUtils;
import uk.ac.ebi.biostudies.index_service.view_counts.ViewCountLoader;

class ViewCountParserTest {

  private ViewCountParser parser;

  private JsonNode mockSubmission;
  private PropertyDescriptor mockPropertyDescriptor;
  private JsonPathService mockJsonPathService;

  @BeforeEach
  void setUp() {
    parser = new ViewCountParser();
    mockSubmission = mock(JsonNode.class);
    mockPropertyDescriptor = mock(PropertyDescriptor.class);
    mockJsonPathService = mock(JsonPathService.class);
  }

  @Test
  void parseReturnsZeroWhenAccessionNotInMap() {
    String accession = "S-NOTFOUND";

    try (var ignored = mockStatic(SubmissionUtils.class)) {
      when(SubmissionUtils.getAccession(mockSubmission)).thenReturn(accession);

      // Ensure map does not contain accession
      ViewCountLoader.unloadViewCountMap();

      String result = parser.parse(mockSubmission, mockPropertyDescriptor, mockJsonPathService);

      assertEquals("0", result);
    }
  }

  @Test
  void parseThrowsExceptionIfSubmissionNull() {
    assertThrows(IllegalArgumentException.class, () -> {
      parser.parse(null, mockPropertyDescriptor, mockJsonPathService);
    });
  }
}
