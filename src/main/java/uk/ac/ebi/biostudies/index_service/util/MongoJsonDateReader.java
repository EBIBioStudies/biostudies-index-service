package uk.ac.ebi.biostudies.index_service.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class MongoJsonDateReader {

  /**
   * Reads a MongoDB exported date from the specified property in the given JSON node. Handles
   * multiple formats:
   * - Canonical format: { "$date": { "$numberLong": "111" } }
   * - Relaxed format: { "$date": "2025-10-02T21:38:27.061Z" }
   * - Shell mode numeric: { "$date": 1764460800000 }
   * - Plain ISO string: "2025-10-02T21:38:27.061Z"
   * - Numeric timestamp: 1764460800000
   *
   * @param submission the JSON node representing the document/submission
   * @param dateProperty the property name expected to hold the date
   * @return the date as milliseconds since epoch, or -1 if no valid date found
   * @throws IllegalArgumentException if the date format is invalid or parsing fails
   */
  public static long readDateMillis(JsonNode submission, String dateProperty) {
    if (submission == null || !submission.hasNonNull(dateProperty)) {
      return -1;
    }

    JsonNode dateNode = submission.get(dateProperty);

    // Handle nested $date formats
    if (dateNode.hasNonNull("$date")) {
      JsonNode dateValue = dateNode.get("$date");

      // Relaxed format: { "$date": "2025-10-02T21:38:27.061Z" }
      if (dateValue.isTextual()) {
        try {
          String dateStr = dateValue.asText();
          return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(dateStr)).toEpochMilli();
        } catch (Exception ex) {
          throw new IllegalArgumentException("Failed to parse ISO_INSTANT date format", ex);
        }
      }

      // Shell mode numeric: { "$date": 1764460800000 }
      if (dateValue.isLong() || dateValue.isInt()) {
        return dateValue.asLong();
      }

      // Canonical format: { "$date": { "$numberLong": "111" } }
      if (dateValue.hasNonNull("$numberLong")) {
        try {
          String millisStr = dateValue.get("$numberLong").asText();
          return Long.parseLong(millisStr);
        } catch (Exception ex) {
          throw new IllegalArgumentException("Failed to parse $numberLong date format", ex);
        }
      }
    }

    // Handle direct numeric timestamp
    if (dateNode.isLong() || dateNode.isInt()) {
      return dateNode.asLong();
    }

    // Handle plain ISO string: "2025-10-02T21:38:27.061Z"
    if (dateNode.isTextual()) {
      String textValue = dateNode.asText();

      // Try parsing as ISO_INSTANT date string first
      if (!textValue.isEmpty()) {
        try {
          return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(textValue)).toEpochMilli();
        } catch (Exception ex) {
          // Not an ISO date, try parsing as numeric string
        }
      }

      // Try parsing as numeric string (e.g., "1764460800000")
      try {
        return Long.parseLong(textValue);
      } catch (Exception ex) {
        // Not a valid numeric string either
      }
    }

    return -1; // no recognizable date format found
  }
}

