package uk.ac.ebi.biostudies.index_service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.Constants;

class ReleaseDateExtractorTest {

  private static final String RELEASE_TIME_ISO = "2021-01-01T00:00:00.000Z";
  private static final String MOD_TIME_ISO = "2021-02-01T00:00:00.000Z";
  private static final long EXAMPLE_RELEASE_TIME = 1609459200000L; // 2021-01-01
  private static final long EXAMPLE_MOD_TIME = 1612137600000L; // 2021-02-01

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void testExtractReleaseDateReturnsReleaseTimeWhenPresent() throws Exception {
    // Given: JSON with release time field
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then
    assertTrue(result.isPresent());
    assertEquals(EXAMPLE_RELEASE_TIME, result.get());
  }

  @Test
  void testExtractReleaseDateReturnsModificationTimeWhenReleaseTimeNotPresentAndReleased() throws Exception {
    // Given: JSON without release time but with modification time and released=true
    String json = String.format(
        "{\"released\": true, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then
    assertTrue(result.isPresent());
    assertEquals(EXAMPLE_MOD_TIME, result.get());
  }

  @Test
  void testExtractReleaseDateReturnsEmptyWhenNoValidDateFound() throws Exception {
    // Given: JSON with no date fields
    String json = "{\"title\": \"Test Submission\"}";
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testExtractReleaseDateReturnsEmptyWhenNotReleasedAndNoReleaseTime() throws Exception {
    // Given: JSON with modification time but released=false, no release time
    String json = String.format(
        "{\"released\": false, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testExtractReleaseDatePrefersReleaseTimeOverModificationTime() throws Exception {
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
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then - should return release time, not modification time
    assertTrue(result.isPresent());
    assertEquals(EXAMPLE_RELEASE_TIME, result.get());
  }

  @Test
  void testExtractReleaseDateWithPlainISOStringFormats() throws Exception {
    // Given: JSON with plain ISO strings (not nested in $date)
    String json = String.format(
        "{\"%s\": \"%s\"}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then
    assertTrue(result.isPresent());
    assertEquals(EXAMPLE_RELEASE_TIME, result.get());
  }

  @Test
  void testExtractReleaseDateWithCanonicalFormat() throws Exception {
    // Given: JSON with MongoDB canonical $numberLong format
    String json = String.format(
        "{\"%s\": {\"$date\": {\"$numberLong\": \"%d\"}}}",
        Constants.RELEASE_TIME_FIELD,
        EXAMPLE_RELEASE_TIME
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then
    assertTrue(result.isPresent());
    assertEquals(EXAMPLE_RELEASE_TIME, result.get());
  }

  @Test
  void testExtractReleaseDateWithNumericShellFormat() throws Exception {
    // Given: JSON with MongoDB shell mode numeric format
    String json = String.format(
        "{\"%s\": {\"$date\": %d}}",
        Constants.RELEASE_TIME_FIELD,
        EXAMPLE_RELEASE_TIME
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then
    assertTrue(result.isPresent());
    assertEquals(EXAMPLE_RELEASE_TIME, result.get());
  }

  @Test
  void testReadReleaseTimeReturnsTimestampWhenPresent() throws Exception {
    // Given: JSON with release time
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    long result = ReleaseDateExtractor.readReleaseTime(submission);

    // Then
    assertEquals(EXAMPLE_RELEASE_TIME, result);
  }

  @Test
  void testReadReleaseTimeReturnsMinusOneWhenMissing() throws Exception {
    // Given: JSON without release time
    String json = "{\"title\": \"Test\"}";
    JsonNode submission = objectMapper.readTree(json);

    // When
    long result = ReleaseDateExtractor.readReleaseTime(submission);

    // Then
    assertEquals(-1L, result);
  }

  @Test
  void testReadModificationDateIfPublicReturnsTimeWhenReleased() throws Exception {
    // Given: JSON with released=true and modification time
    String json = String.format(
        "{\"released\": true, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    long result = ReleaseDateExtractor.readModificationDateIfPublic(submission);

    // Then
    assertEquals(EXAMPLE_MOD_TIME, result);
  }

  @Test
  void testReadModificationDateIfPublicReturnsMinusOneWhenNotReleased() throws Exception {
    // Given: JSON with released=false
    String json = String.format(
        "{\"released\": false, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    long result = ReleaseDateExtractor.readModificationDateIfPublic(submission);

    // Then
    assertEquals(-1L, result);
  }

  @Test
  void testReadModificationDateIfPublicReturnsMinusOneWhenReleasedFieldMissing() throws Exception {
    // Given: JSON without released field
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    long result = ReleaseDateExtractor.readModificationDateIfPublic(submission);

    // Then
    assertEquals(-1L, result);
  }

  @Test
  void testReadModificationDateIfPublicReturnsMinusOneWhenReleasedIsNull() throws Exception {
    // Given: JSON with null released field
    String json = String.format(
        "{\"released\": null, \"%s\": {\"$date\": \"%s\"}}",
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    long result = ReleaseDateExtractor.readModificationDateIfPublic(submission);

    // Then
    assertEquals(-1L, result);
  }

  @Test
  void testExtractReleaseDateWithComplexSubmissionDocument() throws Exception {
    // Given: Realistic submission JSON with all fields
    String json = String.format(
        "{" +
            "  \"accno\": \"S-BSST999\"," +
            "  \"title\": \"Complex Release Date Test\"," +
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
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then - should prefer release time
    assertTrue(result.isPresent());
    assertEquals(EXAMPLE_RELEASE_TIME, result.get());
  }

  @Test
  void testExtractReleaseDateReturnsEmptyWhenReleaseTimeIsZero() throws Exception {
    // Given: JSON with release time as 0
    String json = String.format(
        "{\"%s\": 0}",
        Constants.RELEASE_TIME_FIELD
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testExtractReleaseDateFallsBackWhenReleaseTimeIsNegative() throws Exception {
    // Given: JSON with invalid release time but valid modification time (released=true)
    String json = String.format(
        "{\"released\": true, \"%s\": -1, \"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        Constants.MODIFICATION_TIME_FIELD,
        MOD_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When
    Optional<Long> result = ReleaseDateExtractor.extractReleaseDate(submission);

    // Then - should fall back to modification time
    assertTrue(result.isPresent());
    assertEquals(EXAMPLE_MOD_TIME, result.get());
  }

  @Test
  void testExtractReleaseDateCanBeUsedWithMapAndOrElse() throws Exception {
    // Given: JSON with release time
    String json = String.format(
        "{\"%s\": {\"$date\": \"%s\"}}",
        Constants.RELEASE_TIME_FIELD,
        RELEASE_TIME_ISO
    );
    JsonNode submission = objectMapper.readTree(json);

    // When: Using functional style with map and orElse
    String result = ReleaseDateExtractor.extractReleaseDate(submission)
        .map(String::valueOf)
        .orElse(Constants.NA);

    // Then
    assertEquals(String.valueOf(EXAMPLE_RELEASE_TIME), result);
  }

  @Test
  void testExtractReleaseDateReturnsNAWhenEmpty() throws Exception {
    // Given: JSON with no dates
    String json = "{\"title\": \"Test\"}";
    JsonNode submission = objectMapper.readTree(json);

    // When: Using functional style with orElse
    String result = ReleaseDateExtractor.extractReleaseDate(submission)
        .map(String::valueOf)
        .orElse(Constants.NA);

    // Then
    assertEquals(Constants.NA, result);
  }
}
