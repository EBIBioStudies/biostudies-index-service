package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.Constants;

class CreationTimeParserTest {

  private static final String CREATION_TIME_ISO = "2025-11-30T00:00:00.000Z";
  private static final long EXPECTED_CREATION_TIME_MILLIS = 1764460800000L;

  private CreationTimeParser parser;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    parser = new CreationTimeParser();
    objectMapper = new ObjectMapper();
  }

  @Test
  void testParseWithMongoDateFormat() throws Exception {
    // Given: JSON with MongoDB $date nested format
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.CREATION_TIME_FIELD,
        CREATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_CREATION_TIME_MILLIS), result);
  }

  @Test
  void testParseWithPlainISOString() throws Exception {
    // Given: JSON with plain ISO datetime string
    String json = String.format(
        "{\"%s\": \"%s\"}",
        Constants.CREATION_TIME_FIELD,
        CREATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_CREATION_TIME_MILLIS), result);
  }

  @Test
  void testParseWithMissingCreationTimeField() throws Exception {
    // Given: JSON without creation time field
    String json = "{\"otherField\": \"someValue\"}";
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals("-1", result);
  }

  @Test
  void testParseWithNullCreationTime() throws Exception {
    // Given: JSON with null creation time
    String json = String.format("{\"%s\": null}", Constants.CREATION_TIME_FIELD);
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals("-1", result);
  }

  @Test
  void testParseWithEmptyStringCreationTime() throws Exception {
    // Given: JSON with empty string
    String json = String.format("{\"%s\": \"\"}", Constants.CREATION_TIME_FIELD);
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals("-1", result);
  }

  @Test
  void testParseWithInvalidDateFormat() throws Exception {
    // Given: JSON with invalid date format
    String json = String.format("{\"%s\": \"not-a-valid-date\"}", Constants.CREATION_TIME_FIELD);
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals("-1", result);
  }

  @Test
  void testParseWithMongoDateNumericFormat() throws Exception {
    // Given: JSON with MongoDB $date as numeric timestamp
    String json = String.format(
        "{\"%s\": {\"$date\": %d}}",
        Constants.CREATION_TIME_FIELD,
        EXPECTED_CREATION_TIME_MILLIS
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_CREATION_TIME_MILLIS), result);
  }

  @Test
  void testReadCreationTimeDirectly() throws Exception {
    // Given
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.CREATION_TIME_FIELD,
        CREATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    long result = parser.readCreationTime(submission);

    // Then
    assertEquals(EXPECTED_CREATION_TIME_MILLIS, result);
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
        Constants.CREATION_TIME_FIELD,
        CREATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXPECTED_CREATION_TIME_MILLIS), result);
  }
}

