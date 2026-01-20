package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.Constants;

class ModificationTimeParserTest {

  private static final String MODIFICATION_TIME_ISO = "2025-11-30T00:00:00.000Z";
  private static final long EXPECTED_MODIFICATION_TIME_MILLIS = 1764460800000L;

  private ModificationTimeParser parser;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    parser = new ModificationTimeParser();
    objectMapper = new ObjectMapper();
  }

  @Test
  void testParseWithMongoDateFormat() throws Exception {
    // Given: JSON with MongoDB $date nested format
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MODIFICATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_MODIFICATION_TIME_MILLIS), result);
  }

  @Test
  void testParseWithPlainISOString() throws Exception {
    // Given: JSON with plain ISO datetime string
    String json = String.format(
        "{\"%s\": \"%s\"}",
        Constants.MODIFICATION_TIME_FIELD,
        MODIFICATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_MODIFICATION_TIME_MILLIS), result);
  }

  @Test
  void testParseWithMissingModificationTimeField() throws Exception {
    // Given: JSON without modification time field
    String json = "{\"otherField\": \"someValue\"}";
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertNull(result);
  }

  @Test
  void testParseWithNullModificationTime() throws Exception {
    // Given: JSON with null modification time
    String json = String.format("{\"%s\": null}", Constants.MODIFICATION_TIME_FIELD);
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertNull(result);
  }

  @Test
  void testParseWithEmptyStringModificationTime() throws Exception {
    // Given: JSON with empty string
    String json = String.format("{\"%s\": \"\"}", Constants.MODIFICATION_TIME_FIELD);
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertNull(result);
  }

  @Test
  void testParseWithMongoDateNumericFormat() throws Exception {
    // Given: JSON with MongoDB $date as numeric timestamp
    String json = String.format(
        "{\"%s\": {\"$date\": %d}}",
        Constants.MODIFICATION_TIME_FIELD,
        EXPECTED_MODIFICATION_TIME_MILLIS
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_MODIFICATION_TIME_MILLIS), result);
  }

  @Test
  void testParseWithCanonicalFormat() throws Exception {
    // Given: JSON with MongoDB canonical $numberLong format
    String json = String.format(
        "{\"%s\": {\"$date\": {\"$numberLong\": \"%d\"}}}",
        Constants.MODIFICATION_TIME_FIELD,
        EXPECTED_MODIFICATION_TIME_MILLIS
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_MODIFICATION_TIME_MILLIS), result);
  }

  @Test
  void testReadModificationTimeDirectly() throws Exception {
    // Given: Direct test of helper method
    String json = String.format(
        "{\"%s\": \"%s\"}",
        Constants.MODIFICATION_TIME_FIELD,
        MODIFICATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    long result = parser.readModificationTime(submission);

    // Then
    assertEquals(EXPECTED_MODIFICATION_TIME_MILLIS, result);
  }

  @Test
  void testParseWithComplexSubmissionDocument() throws Exception {
    // Given: More realistic submission JSON with nested structures
    String json = String.format(
        "{" +
            "  \"accno\": \"S-BSST123\"," +
            "  \"title\": \"Test Submission\"," +
            "  \"%s\": {\"$date\": \"%s\"}," +
            "  \"released\": true," +
            "  \"collections\": []" +
            "}",
        Constants.MODIFICATION_TIME_FIELD,
        MODIFICATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_MODIFICATION_TIME_MILLIS), result);
  }
}
