package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * A parser implementation that extracts attribute values from a submission JSON based on metadata
 * defined in a PropertyDescriptor.
 *
 * <p>This parser uses JSONPath expressions specified in the property descriptor to locate values
 * within the submission's section attributes array, matching attributes by name (case-insensitive).
 * If a regex pattern is configured in the descriptor, it applies the pattern to extracted values to
 * filter or refine them.
 *
 * <p>The parser supports different processing strategies depending on the configured field type in
 * the descriptor:
 *
 * <ul>
 *   <li>Facet fields: multiple matching values are joined with a delimiter.
 *   <li>Long integer fields: a single value is parsed to Long or counts the number of values.
 *   <li>Default: values are concatenated into a space-separated string.
 * </ul>
 *
 * <p>The parser is stateless and returns the extracted value as a String without side effects.
 *
 * <p>Designed for use within a BioStudies indexing system, it relies on structured collection and
 * property metadata from the CollectionRegistry to drive extraction rules.
 */
@Component("SimpleAttributeParser")
public class SimpleAttributeParser implements Parser {

  private static final String ATTRIBUTE_NAME_PATH_TEMPLATE =
      "$.section.attributes[?(@.name=~ /%s/i)].value";
  private final Logger logger = LogManager.getLogger(SimpleAttributeParser.class.getName());

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
    String result;

    String jsonPathExpression = buildJsonPathExpression(propertyDescriptor.getTitle());
    List<String> resultData = jsonPathService.read(submission, jsonPathExpression);

    if (propertyDescriptor.hasMatch()) {
      resultData = evaluateRegexp(propertyDescriptor, resultData);
    }

    FieldType fieldType = propertyDescriptor.getFieldType();
    switch (fieldType) {
      case FACET -> result = String.join(Constants.FACET_VALUE_DELIMITER, resultData);
      case LONG -> result = processLongValue(propertyDescriptor, resultData);
      default -> result = String.join(" ", resultData);
    }

    return result;
  }

  String buildJsonPathExpression(String propertyTitle) {
    String formattedPropertyTitle = StringUtils.replace(propertyTitle, "/", "\\/");
    return String.format(ATTRIBUTE_NAME_PATH_TEMPLATE, formattedPropertyTitle);
  }

  /**
   * Tries to apply a regexp on a list of strings that represent the extracted value from a field in
   * the JSON. If not possible, this would return {@code Constants.NA} and log the exception
   *
   * @param propertyDescriptor {@code PropertyDescriptor} object with the property information
   * @param resultData Data to evaluate
   * @return Evaluated data
   */
  private List<String> evaluateRegexp(
      PropertyDescriptor propertyDescriptor, List<String> resultData) {
    String regexp = propertyDescriptor.getMatch();
    List<String> evaluatedData;
    try {
      evaluatedData =
          resultData.stream()
              .map(
                  item -> {
                    Matcher matcher = Pattern.compile(regexp).matcher(item);
                    return matcher.find() ? matcher.group(1) : "";
                  })
              .collect(Collectors.toList());
    } catch (PatternSyntaxException e) {
      String errorMessage =
          String.format("Regular expression syntax error: %s -> %s", regexp, e.getMessage());
      logger.error(errorMessage);
      logger.error(propertyDescriptor);
      throw new IllegalArgumentException(errorMessage);
    }
    return evaluatedData;
  }

  /**
   * Processes a list of string values as a long. Returns the parsed long if exactly one value is
   * present; otherwise returns the count of the values.
   *
   * <p>Logs an error and returns 0 if parsing fails.
   */
  private String processLongValue(PropertyDescriptor propertyDescriptor, List<String> resultData) {
    String result = "";
    if (resultData.isEmpty()) {
      return result;
    }

    if (resultData.size() == 1) {
      String longAsString = resultData.getFirst();
      try {
        long parsed = Long.parseLong(longAsString);
        return Long.toString(parsed);
      } catch (NumberFormatException e) {
        String errorMessage =
            String.format(
                "Error trying to parse a long value: %s -> %s", longAsString, e.getMessage());
        logger.error(errorMessage);
        logger.error(propertyDescriptor);
        return result;
      }
    } else {
      return Integer.toString(resultData.size());
    }
  }
}
