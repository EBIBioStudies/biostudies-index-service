package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.lucene.document.DateTools;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.util.MongoJsonDateReader;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * A parser component that extracts the modification year from a submission's JSON representation.
 * <p>
 * This parser expects the modification time to be present as an ISO 8601 instant string nested within
 * the JSON path "modification" -> "$date". It converts this datetime into epoch milliseconds, then
 * extracts and returns the year component as a formatted string.
 */
@Component("ModificationYearParser")
public class ModificationYearParser implements Parser {

  /**
   * Parses the modification time from the submission JSON node. The supported format is ISO_INSTANT
   * date-time string nested inside "modification" -> "$date" field. From it, extracts the year.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor descriptor containing the property metadata (not used here)
   * @param jsonPathService service for JSONPath evaluation (not used here)
   * @return the modification time as a String representing the year extracted from milliseconds since epoch,
   *     or an empty string if no modification time found
   * @throws IllegalArgumentException if the date format is invalid or any parsing error occurs
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    long modificationDateLong = readModificationTime(submission);
    if (modificationDateLong < 0) {
      return null;
    }
    return DateTools.timeToString(modificationDateLong, DateTools.Resolution.YEAR);
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
