package uk.ac.ebi.biostudies.index_service.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import uk.ac.ebi.biostudies.index_service.Constants;

/**
 * Utility class for extracting the release date from submission JSON nodes.
 * <p>
 * This class provides helper methods to determine the appropriate release date by prioritizing
 * the explicit release time field, and falling back to the modification time only if the
 * submission is marked as released/public.
 * <p>
 * This utility is used by multiple parsers that require release date extraction logic.
 */
public final class ReleaseDateExtractor {

  // Private constructor to prevent instantiation
  private ReleaseDateExtractor() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Extracts the release date from the submission JSON node. Attempts to read the release time
   * field first; if not present or invalid, falls back to the modification time only if the
   * submission is marked as released/public.
   *
   * @param submission the JSON node representing the submission to parse (can be null)
   * @return an Optional containing the release date as milliseconds since epoch, or empty if no
   *     valid release date is found or submission is null
   * @throws IllegalArgumentException if the date format is invalid or any parsing error occurs
   */
  public static Optional<Long> extractReleaseDate(JsonNode submission) {
    if (submission == null) {
      return Optional.empty();
    }

    long releaseTime = readReleaseTime(submission);

    // Use the releaseTime value if present and valid
    if (releaseTime > 0) {
      return Optional.of(releaseTime);
    }

    // Fall back to modification time if submission is released/public
    long modificationTime = readModificationDateIfPublic(submission);
    if (modificationTime > 0) {
      return Optional.of(modificationTime);
    }

    return Optional.empty();
  }

  /**
   * Reads the release time from the submission JSON node. The time may be stored as an ISO8601
   * datetime string or nested MongoDB date format, and is converted to epoch milliseconds.
   *
   * @param submission the JSON node representing the submission
   * @return the release time in milliseconds since epoch, or -1 if not found
   * @throws IllegalArgumentException if the date parsing fails
   */
  static long readReleaseTime(JsonNode submission) {
    return MongoJsonDateReader.readDateMillis(submission, Constants.RELEASE_TIME_FIELD);
  }

  /**
   * Reads the modification time from the submission if the submission is marked as released
   * (public). Returns -1 if the submission is not released or if no valid modification time is
   * found.
   *
   * @param submission the JSON node representing the submission
   * @return the modification time in milliseconds since epoch, or -1 if not applicable
   * @throws IllegalArgumentException if the date parsing fails
   */
  static long readModificationDateIfPublic(JsonNode submission) {
    if (submission == null) {
      return -1;
    }

    if (submission.hasNonNull(Constants.RELEASED_FIELD)
        && submission.get(Constants.RELEASED_FIELD).asBoolean(false)) {
      return MongoJsonDateReader.readDateMillis(submission, Constants.MODIFICATION_TIME_FIELD);
    }
    return -1;
  }
}
