package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.lucene.document.DateTools;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.util.ReleaseDateExtractor;

/**
 * Parser component that extracts the release year from a submission.
 * <p>
 * This parser determines the appropriate release date by prioritizing the explicit release time
 * field, and falling back to the modification time only if the submission is marked as
 * released/public. The extracted timestamp is converted to a year string using Lucene's DateTools.
 * <p>
 * Returns "N/A" if no valid release date can be determined.
 *
 * @see ReleaseDateExtractor
 * @see ReleaseTimeParser
 * @see ReleaseDateParser
 */
@Component("ReleaseYearParser")
public class ReleaseYearParser implements Parser {

  /**
   * Parses the release year from the submission JSON node. Attempts to read the release time field
   * first; if not present, falls back to the modification time only if the submission is marked as
   * released/public. Extracts and returns the year component from the timestamp.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor descriptor containing the property metadata (not used)
   * @param jsonPathService service for JSONPath evaluation (not used)
   * @return the release year as a Lucene-formatted year string, or "N/A" if no valid release date
   *     is found
   * @throws IllegalArgumentException if the date format is invalid or any parsing error occurs
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    return ReleaseDateExtractor.extractReleaseDate(submission)
        .map(timestamp -> DateTools.timeToString(timestamp, DateTools.Resolution.YEAR))
        .orElse(Constants.NA);
  }
}