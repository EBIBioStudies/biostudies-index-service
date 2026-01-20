package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.document.DateTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.Constants;

class ReleaseYearParserTest {

  private static final String RELEASE_TIME_2021_ISO = "2021-01-01T00:00:00.000Z";
  private static final String RELEASE_TIME_2023_ISO = "2023-06-15T14:30:00.000Z";
  private static final String MOD_TIME_2022_ISO = "2022-02-01T00:00:00.000Z";
  private static final long RELEASE_TIME_2021_MILLIS = 1609459200000L; // 2021-01-01
  private static final long RELEASE_TIME_2023_MILLIS = 1686841800000L; // 2023-06-15
  private static final long MOD_TIME_2022_MILLIS = 1643673600000L; // 2022-02-01

  private ReleaseYearParser parser;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    parser = new ReleaseYearParser();
    objectMapper = new ObjectMapper();
  }

  @Test
  void testParseReturnsYearFromReleaseTime() throws Exception {
    // Given: JSON with release time field
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_2021_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(RELEASE_TIME_2021_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
  }

  @Test
  void testParseReturnsYearFromModificationTimeWhenReleaseTimeNotPresentAndReleased() throws Exception {
    // Given: JSON without release time but with modification time and released=true
    String json = String.format(
        "{\"released\": true, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_2022_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(MOD_TIME_2022_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
  }

  @Test
  void testParseReturnsNAWhenNoValidDateFound() throws Exception {
    // Given: JSON with no date fields
    String json = "{\"title\": \"Test Submission\"}";
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(Constants.NA, result);
  }

  @Test
  void testParseReturnsNAWhenNotReleasedAndNoReleaseTime() throws Exception {
    // Given: JSON with modification time but released=false, no release time
    String json = String.format(
        "{\"released\": false, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_2022_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(Constants.NA, result);
  }

  @Test
  void testParsePrefersReleaseTimeOverModificationTime() throws Exception {
    // Given: JSON with both release time and modification time
    String json = String.format(
        "{\"released\": true, \"%s\": {\"$date\": \"%s\"}, \"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_2021_ISO,
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_2022_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(RELEASE_TIME_2021_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then - should return 2021, not 2022
    assertEquals(expectedYear, result);
  }

  @Test
  void testParseWithPlainISOStringFormat() throws Exception {
    // Given: JSON with plain ISO strings (not nested in $date)
    String json = String.format(
        "{\"%s\": \"%s\"}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_2023_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(RELEASE_TIME_2023_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
  }

  @Test
  void testParseWithCanonicalFormat() throws Exception {
    // Given: JSON with MongoDB canonical $numberLong format
    String json = String.format(
        "{\"%s\": {\"$date\": {\"$numberLong\": \"%d\"}}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_2021_MILLIS
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(RELEASE_TIME_2021_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
  }

  @Test
  void testParseWithNumericShellFormat() throws Exception {
    // Given: JSON with MongoDB shell mode numeric format
    String json = String.format(
        "{\"%s\": {\"$date\": %d}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_2021_MILLIS
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(RELEASE_TIME_2021_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then
    assertEquals(expectedYear, result);
  }

  @Test
  void testParseWithDifferentYears() throws Exception {
    // Given: Test multiple years to verify year extraction works correctly
    String[][] testCases = {
        {"2020-01-15T10:30:00.000Z", "2020"},
        {"2023-06-20T14:45:00.000Z", "2023"},
        {"2025-12-31T23:59:59.999Z", "2025"}
    };

    for (String[] testCase : testCases) {
      String dateString = testCase[0];
      String json = String.format("{\"%s\": \"%s\"}", Constants.RELEASE_TIME_FIELD, dateString);
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
    // Given: Realistic submission JSON with all fields
    String json = String.format(
        "{" +
            "  \"accno\": \"S-BSST456\"," +
            "  \"title\": \"Year Parser Test\"," +
            "  \"released\": true," +
            "  \"%s\": {\"$date\": \"%s\"}," +
            "  \"%s\": {\"$date\": \"%s\"}," +
            "  \"owner\": \"test-user\"," +
            "  \"collections\": []" +
            "}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_2021_ISO,
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_2022_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(RELEASE_TIME_2021_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then - should prefer release time (2021)
    assertEquals(expectedYear, result);
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
    assertEquals(Constants.NA, result);
  }

  @Test
  void testParseFallsBackWhenReleaseTimeIsNegative() throws Exception {
    // Given: JSON with invalid release time but valid modification time (released=true)
    String json = String.format(
        "{\"released\": true, \"%s\": -1, \"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_2022_ISO
    );
    JsonNode submission = objectMapper.readTree(json);
    String expectedYear = DateTools.timeToString(MOD_TIME_2022_MILLIS, DateTools.Resolution.YEAR);

    // When
    String result = parser.parse(submission, null, null);

    // Then - should fall back to modification time (2022)
    assertEquals(expectedYear, result);
  }

  @Test
  void testParseWithNullSubmission() {
    // Given: null submission
    // When
    String result = parser.parse(null, null, null);

    // Then
    assertEquals(Constants.NA, result);
  }
}
