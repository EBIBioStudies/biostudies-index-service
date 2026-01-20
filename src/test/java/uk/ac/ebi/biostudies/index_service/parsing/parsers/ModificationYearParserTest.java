package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.document.DateTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.Constants;

class ModificationYearParserTest {

  private static final String MODIFICATION_TIME_ISO = "2025-11-30T00:00:00.000Z";
  private static final long EXPECTED_MODIFICATION_TIME_MILLIS = 1764460800000L;

  private ModificationYearParser parser;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    parser = new ModificationYearParser();
    objectMapper = new ObjectMapper();
  }

  @Test
  void testParseReturnsYearFromMongoDateFormat() throws Exception {
    // Given: JSON with MongoDB $date nested format
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MODIFICATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(EXPECTED_MODIFICATION_TIME_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
  }

  @Test
  void testParseReturnsYearFromPlainISOString() throws Exception {
    // Given: JSON with plain ISO datetime string
    String json = String.format(
        "{\"%s\": \"%s\"}",
        Constants.MODIFICATION_TIME_FIELD,
        MODIFICATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(EXPECTED_MODIFICATION_TIME_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
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
  void testParseWithCanonicalFormat() throws Exception {
    // Given: JSON with MongoDB canonical $numberLong format
    String json = String.format(
        "{\"%s\": {\"$date\": {\"$numberLong\": \"%d\"}}}",
        Constants.MODIFICATION_TIME_FIELD,
        EXPECTED_MODIFICATION_TIME_MILLIS
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(EXPECTED_MODIFICATION_TIME_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
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
    String expectedYear = DateTools.timeToString(EXPECTED_MODIFICATION_TIME_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
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
  void testParseWithDifferentYears() throws Exception {
    // Given: Test multiple years to verify year extraction works correctly
    String[] testDates = {
        "2020-01-15T10:30:00.000Z",  // 2020
        "2023-06-20T14:45:00.000Z",  // 2023
        "2025-12-31T23:59:59.999Z"   // 2025
    };

    for (String dateString : testDates) {
      String json = String.format("{\"%s\": \"%s\"}", Constants.MODIFICATION_TIME_FIELD, dateString);
      JsonNode submission = objectMapper.readTree(json);
      long millis = java.time.Instant.parse(dateString).toEpochMilli();
      String expectedYear = DateTools.timeToString(millis, DateTools.Resolution.YEAR);

      // When
      String result = parser.parse(submission, null, null);

      // Then
      assertEquals(expectedYear, result, "Failed for date: " + dateString);
    }
  }

  @Test
  void testParseWithComplexSubmissionDocument() throws Exception {
    // Given: More realistic submission JSON with nested structures
    String json = String.format(
        "{" +
            "  \"accno\": \"S-BSST456\"," +
            "  \"title\": \"Year Parser Test\"," +
            "  \"%s\": {\"$date\": \"%s\"}," +
            "  \"released\": true," +
            "  \"owner\": \"test-user\"" +
            "}",
        Constants.MODIFICATION_TIME_FIELD,
        MODIFICATION_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(EXPECTED_MODIFICATION_TIME_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
  }
}
