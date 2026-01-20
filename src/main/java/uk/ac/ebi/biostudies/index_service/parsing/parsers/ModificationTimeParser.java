package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.util.MongoJsonDateReader;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * A parser component responsible for extracting the modification time from a submission JSON node.
 * <p>
 * This parser expects the modification time to be represented as an ISO 8601 instant date-time string,
 * nested inside the JSON path "modification" -> "$date". It parses this datetime string and
 * converts it into milliseconds since the epoch (January 1, 1970 UTC).
 * <p>
 * Implements the {@link Parser} interface and integrates with the parsing framework for submission
 * metadata processing.
 */
@Component("ModificationTimeParser")
public class ModificationTimeParser implements Parser {

  /**
   * Parses the modification time from the submission JSON node. The supported format is ISO_INSTANT
   * date-time string nested inside "modification" -> "$date" field.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor descriptor containing the property metadata (not used here)
   * @param jsonPathService service for JSONPath evaluation (not used here)
   * @return the modification time as a String representing the milliseconds since epoch, or an empty
   *     string if no modification time found
   * @throws IllegalArgumentException if the date format is invalid or any parsing error occurs
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    long modificationDateLong = readModificationTime(submission);
    if (modificationDateLong < 0) {
      return null;
    }
    return String.valueOf(modificationDateLong);
  }

  /**
   * Reads the modification time from the submission JsonNode, where the modification time is expected to be
   * stored as a full ISO8601 datetime string or nested MongoDB date format. Uses a helper utility
   * (MongoJsonDateReader) that parses the date/time string and converts it to epoch milliseconds.
   *
   * @param submission the JsonNode representing the submission containing the full modification time
   *     field
   * @return the modification time in milliseconds since the epoch as a String
   * @throws IllegalArgumentException if the date parsing fails (delegated to the
   *     MongoJsonDateReader)
   */
  long readModificationTime(JsonNode submission) {
    return MongoJsonDateReader.readDateMillis(submission, Constants.MODIFICATION_TIME_FIELD);
  }
}
