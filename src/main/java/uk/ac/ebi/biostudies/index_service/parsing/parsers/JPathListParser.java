package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * JPathListParser is a parser component designed to extract and process values from a JSON
 * submission using a list of JSONPath expressions provided in the property descriptor. It
 * aggregates matching values from multiple JSONPaths into a single result string or a summed long
 * value depending on the specified field type.
 *
 * <p>The parser supports configurable transformations such as converting the output to lowercase.
 *
 * <p>This implementation is typically used within a larger indexing or data processing system where
 * metadata fields need to be dynamically extracted and formatted from structured JSON inputs.
 */
@Component("JPathListParser")
public class JPathListParser implements Parser {

  /**
   * Parses the given submission JSON node using the provided property descriptor and returns the
   * parsed value as a String.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor the descriptor object containing field metadata and parsing
   *     instructions
   * @param jsonPathService the service in charge of evaluating JSONPath expressions
   * @return the parsed value as a String, never null (empty string if no value found)
   * @throws IllegalArgumentException if any error occurs during parsing
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    Object result;

    List<String> resultData = jsonPathService.read(submission, propertyDescriptor);

    FieldType fieldType = propertyDescriptor.getFieldType();
    switch (fieldType) {
      case FACET -> result = String.join(Constants.FACET_VALUE_DELIMITER, resultData);
      case LONG -> result = resultData.stream().mapToLong(Long::parseLong).sum();
      default -> result = String.join(" ", resultData);
    }

    if (Boolean.TRUE.equals(propertyDescriptor.getToLowerCase())) {
      result = result.toString().toLowerCase();
    }

    return result.toString();
  }
}
