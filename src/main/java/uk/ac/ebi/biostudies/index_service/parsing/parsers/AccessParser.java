package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * Parser that extracts access-related information from a submission JSON node by evaluating
 * multiple JSONPath expressions specified in the provided {@link PropertyDescriptor}. It aggregates
 * all matched values into a single string, normalizing it to lowercase.
 *
 * <p>The parser also inspects the "released" field in the submission JSON; if present and true, it
 * appends the constant {@link Constants#PUBLIC} to the aggregated access values.
 *
 * <p>This class is intended for producing a normalized, concatenated string representing access
 * tags, collection accession numbers, owner names, and release status for faceting or filtering in
 * indexing workflows.
 */
@Component("AccessParser")
public class AccessParser implements Parser {

  /**
   * Parses the given submission JSON node using the provided property descriptor and JSONPath
   * service, extracting access-related values.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor the descriptor object containing field metadata and JSONPath
   *     expressions
   * @param jsonPathService the service responsible for evaluating JSONPath expressions on the
   *     submission
   * @return the space-delimited, lowercase string of unique access values extracted from JSONPaths
   *     and release status. Returns an empty string if no values are found.
   * @throws IllegalArgumentException if the submission or property descriptor are invalid or
   *     parsing fails
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {

    // Evaluate the configured JSONPaths to obtain access-related values
    List<String> accessValues = jsonPathService.read(submission, propertyDescriptor);

    // Append 'public' if the submission's 'released' flag is set to true
    boolean isReleased = submission.has("released") && submission.get("released").asBoolean(false);
    boolean needsPublicFlag =
        isReleased && accessValues.stream().noneMatch(Constants.PUBLIC::equalsIgnoreCase);

    if (needsPublicFlag) {
      accessValues = new ArrayList<>(accessValues); // Only create copy when modification needed
      accessValues.add(Constants.PUBLIC);
    }

    // Concatenate values with spaces and convert to lowercase for normalized output
    return String.join(" ", accessValues).toLowerCase();
  }
}
