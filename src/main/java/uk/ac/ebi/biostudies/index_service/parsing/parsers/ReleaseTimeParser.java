package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.util.ReleaseDateExtractor;

/**
 * Parser component that extracts and determines the release date for a submission. This parser
 * prioritizes the explicit release time field, falling back to the modification time if the
 * submission is marked as public and no release time exists. Returns the date as milliseconds since
 * epoch or "N/A" if unavailable.
 */
@Component("ReleaseTimeParser")
public class ReleaseTimeParser implements Parser {

  /**
   * Parses the release date from the submission JSON node. Attempts to read the release time field
   * first; if not present, falls back to the modification time only if the submission is marked as
   * released/public.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor descriptor containing the property metadata (not used)
   * @param jsonPathService service for JSONPath evaluation (not used)
   * @return the release date as a String representing milliseconds since epoch, or "N/A" if no
   *     valid release date is found
   * @throws IllegalArgumentException if the date format is invalid or any parsing error occurs
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    return ReleaseDateExtractor.extractReleaseDate(submission)
        .map(String::valueOf)
        .orElse(null);
  }
}
