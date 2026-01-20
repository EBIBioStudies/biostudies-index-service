package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.util.MongoJsonDateReader;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * A parser component responsible for extracting the creation time from a submission JSON node.
 * <p>
 * This parser expects the creation time to be represented as an ISO 8601 instant date-time string,
 * nested inside the JSON path "creationTime" -> "$date". It parses this datetime string and
 * converts it into milliseconds since the epoch (January 1, 1970 UTC).
 */
@Component("CreationTimeParser")
public class CreationTimeParser implements Parser {

  /**
   * Parses the creation time from the submission JSON node. The supported format is ISO_INSTANT
   * date-time string nested inside "creationTime" -> "$date" field.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor descriptor containing the property metadata (not used here)
   * @param jsonPathService service for JSONPath evaluation (not used here)
   * @return the creation time as a String representing the milliseconds since epoch, or an empty
   *     string if no creation time found
   * @throws IllegalArgumentException if the date format is invalid or any parsing error occurs
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    long creationDateLong = readCreationTime(submission);
    return String.valueOf(creationDateLong);
  }

  /**
   * Reads the creation time from the submission JsonNode, where the creation time is expected to be
   * stored as a full ISO8601 datetime string or nested MongoDB date format. Uses a helper utility
   * (MongoJsonDateReader) that parses the date/time string and converts it to epoch milliseconds.
   *
   * @param submission the JsonNode representing the submission containing the full creation time
   *     field
   * @return the creation time in milliseconds since the epoch
   * @throws IllegalArgumentException if the date parsing fails (delegated to the
   *     MongoJsonDateReader)
   */
  long readCreationTime(JsonNode submission) {
    return MongoJsonDateReader.readDateMillis(submission, Constants.CREATION_TIME_FIELD);
  }
}
