package uk.ac.ebi.biostudies.index_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.util.MongoJsonDateReader;

class MongoJsonDateReaderTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  @Test
  void testReadDateMillisRelaxedFormat() throws Exception {
    // Given: MongoDB relaxed format with $date wrapper
    String json = "{ \"dateProp\": { \"$date\": \"2025-10-02T21:38:27.061Z\" } }";
    JsonNode node = mapper.readTree(json);
    long expected = Instant.parse("2025-10-02T21:38:27.061Z").toEpochMilli();

    // When & Then
    assertEquals(expected, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisPlainISOString() throws Exception {
    // Given: Plain ISO string without $date wrapper
    String json = "{ \"dateProp\": \"2025-10-02T21:38:27.061Z\" }";
    JsonNode node = mapper.readTree(json);
    long expected = Instant.parse("2025-10-02T21:38:27.061Z").toEpochMilli();

    // When & Then
    assertEquals(expected, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisCanonicalFormat() throws Exception {
    // Given: MongoDB canonical format with $numberLong
    String json = "{ \"dateProp\": { \"$date\": { \"$numberLong\": \"111\" } } }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertEquals(111L, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisDirectLong() throws Exception {
    // Given: Direct numeric timestamp
    String json = "{ \"dateProp\": 12345 }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertEquals(12345L, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisDirectLongAsString() throws Exception {
    // Given: Numeric timestamp as string
    String json = "{ \"dateProp\": \"67890\" }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertEquals(67890L, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisMissingProperty() throws Exception {
    // Given: JSON without the requested property
    String json = "{ \"anotherProp\": 100 }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertEquals(-1L, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisNullProperty() throws Exception {
    // Given: Property exists but is null
    String json = "{ \"dateProp\": null }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertEquals(-1L, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisEmptyString() throws Exception {
    // Given: Empty string value
    String json = "{ \"dateProp\": \"\" }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertEquals(-1L, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisMalformedISODateInRelaxedFormat() throws Exception {
    // Given: Malformed ISO date in $date wrapper
    String json = "{ \"dateProp\": { \"$date\": \"not-a-date\" } }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertThrows(IllegalArgumentException.class,
        () -> MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisMalformedISODatePlainString() throws Exception {
    // Given: Malformed ISO date as plain string (should return -1, not throw)
    String json = "{ \"dateProp\": \"not-a-date-or-number\" }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertEquals(-1L, MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisMalformedNumberLongThrows() throws Exception {
    // Given: Invalid $numberLong value
    String json = "{ \"dateProp\": { \"$date\": { \"$numberLong\": \"bad-number\" } } }";
    JsonNode node = mapper.readTree(json);

    // When & Then
    assertThrows(IllegalArgumentException.class,
        () -> MongoJsonDateReader.readDateMillis(node, "dateProp"));
  }

  @Test
  void testReadDateMillisNullSubmission() {
    // Given: Null submission node
    // When & Then
    assertEquals(-1L, MongoJsonDateReader.readDateMillis(null, "dateProp"));
  }

  @Test
  void testReadDateMillisWithMillisecondPrecision() throws Exception {
    // Given: ISO date with full millisecond precision
    String json = "{ \"creationTime\": \"2025-11-30T00:00:00.123Z\" }";
    JsonNode node = mapper.readTree(json);
    long expected = Instant.parse("2025-11-30T00:00:00.123Z").toEpochMilli();

    // When & Then
    assertEquals(expected, MongoJsonDateReader.readDateMillis(node, "creationTime"));
  }
}
