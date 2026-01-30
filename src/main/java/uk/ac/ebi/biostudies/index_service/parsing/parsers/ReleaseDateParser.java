package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.SimpleDateFormat;
import org.apache.lucene.document.DateTools;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.util.ReleaseDateExtractor;

/**
 * Parser component that extracts and formats the release date from a submission as a simple date
 * string (yyyy-MM-dd).
 *
 * <p>This parser determines the appropriate release date by prioritizing the explicit release time
 * field, and falling back to the modification time only if the submission is marked as
 * released/public. The extracted timestamp is rounded to day precision using Lucene's DateTools and
 * formatted as "yyyy-MM-dd".
 *
 * <p>Returns "N/A" if no valid release date can be determined.
 *
 * @see ReleaseDateExtractor
 * @see ReleaseTimeParser
 */
@Component("ReleaseDateParser")
public class ReleaseDateParser implements Parser {

  private final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

  /**
   * Parses the release date from the submission JSON node and formats it as a simple date string
   * (yyyy-MM-dd). Attempts to read the release time field first; if not present, falls back to the
   * modification time only if the submission is marked as released/public.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor descriptor containing the property metadata (not used)
   * @param jsonPathService service for JSONPath evaluation (not used)
   * @return the release date formatted as "yyyy-MM-dd", or "N/A" if no valid release date is found
   * @throws IllegalArgumentException if the date format is invalid or any parsing error occurs
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    return ReleaseDateExtractor.extractReleaseDate(submission)
        .map(timestamp -> DateTools.round(timestamp, DateTools.Resolution.DAY))
        .map(SIMPLE_DATE_FORMAT::format)
        .orElse(Constants.NA);
  }
}
