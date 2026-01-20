package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.Constants;

class ReleaseTimeParserTest {

  private static final String RELEASE_TIME_ISO = "2021-01-01T00:00:00.000Z";
  private static final String MOD_TIME_ISO = "2021-02-01T00:00:00.000Z";
  private static final long EXAMPLE_RELEASE_TIME = 1609459200000L; // 2021-01-01
  private static final long EXAMPLE_MOD_TIME = 1612137600000L; // 2021-02-01

  private ReleaseTimeParser parser;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    parser = new ReleaseTimeParser();
    objectMapper = new ObjectMapper();
  }

  @Test
  void testParseReturnsReleaseTimeWhenPresent() throws Exception {
    // Given: JSON with release time field
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXAMPLE_RELEASE_TIME), result);
  }

  @Test
  void testParseReturnsModificationTimeWhenReleaseTimeNotPresentAndReleased() throws Exception {
    // Given: JSON without release time but with modification time and released=true
    String json = String.format(
        "{\"released\": true, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXAMPLE_MOD_TIME), result);
  }

  @Test
  void testParseReturnsNAWhenNoValidDateFound() throws Exception {
    // Given: JSON with no date fields
    String json = "{\"title\": \"Test Submission\"}";
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertNull(result);
  }

  @Test
  void testParseReturnsNAWhenNotReleasedAndNoReleaseTime() throws Exception {
    // Given: JSON with modification time but released=false, no release time
    String json = String.format(
        "{\"released\": false, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertNull(result);
  }

  @Test
  void testParsePrefersReleaseTimeOverModificationTime() throws Exception {
    // Given: JSON with both release time and modification time
    String json = String.format(
        "{\"released\": true, \"%s\": {\"$date\": \"%s\"}, \"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_ISO,
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then - should return release time, not modification time
    assertEquals(String.valueOf(EXAMPLE_RELEASE_TIME), result);
  }

  @Test
  void testParseWithPlainISOStringFormats() throws Exception {
    // Given: JSON with plain ISO strings (not nested in $date)
    String json = String.format(
        "{\"released\": true, \"%s\": \"%s\", \"%s\": \"%s\"}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_ISO,
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXAMPLE_RELEASE_TIME), result);
  }

  @Test
  void testParseWithCanonicalFormat() throws Exception {
    // Given: JSON with MongoDB canonical $numberLong format
    String json = String.format(
        "{\"%s\": {\"$date\": {\"$numberLong\": \"%d\"}}}",
        Constants.RELEASE_TIME_FIELD,
        EXAMPLE_RELEASE_TIME
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXAMPLE_RELEASE_TIME), result);
  }

  @Test
  void testParseWithNumericShellFormat() throws Exception {
    // Given: JSON with MongoDB shell mode numeric format
    String json = String.format(
        "{\"%s\": {\"$date\": %d}}",
        Constants.RELEASE_TIME_FIELD,
        EXAMPLE_RELEASE_TIME
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(String.valueOf(EXAMPLE_RELEASE_TIME), result);
  }

  @Test
  void testParseWithComplexSubmissionDocument() throws Exception {
    // Given: Realistic submission JSON with all fields
    String json = String.format(
        "{" +
            "  \"accno\": \"S-BSST789\"," +
            "  \"title\": \"Complex Submission\"," +
            "  \"released\": true," +
            "  \"%s\": {\"$date\": \"%s\"}," +
            "  \"%s\": {\"$date\": \"%s\"}," +
            "  \"owner\": \"test-user\"," +
            "  \"collections\": []" +
            "}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_ISO,
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then - should prefer release time
    assertEquals(String.valueOf(EXAMPLE_RELEASE_TIME), result);
  }

  @Test
  void testParseReturnsNAWhenReleaseTimeIsZero() throws Exception {
    // Given: JSON with release time as 0
    String json = String.format(
        "{\"%s\": 0}",
        Constants.RELEASE_TIME_FIELD
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertNull(result);
  }

  @Test
  void testParseFallsBackWhenReleaseTimeIsNegative() throws Exception {
    // Given: JSON with invalid release time but valid modification time (released=true)
    String json = String.format(
        "{\"released\": true, \"%s\": -1, \"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then - should fall back to modification time
    assertEquals(String.valueOf(EXAMPLE_MOD_TIME), result);
  }

  @Test
  void testParseWithNullSubmission() {
    // Given: null submission
    // When
    String result = parser.parse(null, null, null);

    // Then
    assertNull(result);
  }
}
