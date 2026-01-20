package uk.ac.ebi.biostudies.index_service.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * Service for evaluating JSONPath expressions on JSON submissions.
 *
 * <p>This class delegates the actual reading of JSONPath expressions to a {@link JsonPathReader}
 * component and aggregates the results into a single list.
 */
@Service
public class JsonPathService {

  private static final Logger logger = LogManager.getLogger(JsonPathService.class.getName());
  private final JsonPathReader jsonPathReader;

  /**
   * Constructs a <code>JsonPathService</code> with the specified reader.
   *
   * @param jsonPathReader the low-level JSONPath reader used to execute JSONPath queries
   */
  public JsonPathService(JsonPathReader jsonPathReader) {
    this.jsonPathReader = jsonPathReader;
  }

  /**
   * Reads and evaluates a single JSONPath expression against the provided submission JSON node.
   *
   * <p>Results are processed to ensure uniqueness and preserve insertion order. Values are trimmed
   * of leading and trailing whitespace, and null or empty values are excluded from the result.
   *
   * @param submission the root JSON node representing the submission data
   * @param jsonPathExpression the JSONPath expression to evaluate
   * @return a list of unique, trimmed string values in insertion order; an empty list if no matches
   *     are found or all values are null/empty
   * @throws IllegalArgumentException if submission or jsonPathExpression are null or invalid
   * @throws ParsingException if JSONPath evaluation fails due to malformed expression or other
   *     errors
   */
  public List<String> read(JsonNode submission, String jsonPathExpression) {
    List<String> rawResults = jsonPathReader.read(submission, jsonPathExpression);
    return normalizeResults(rawResults);
  }

  /**
   * Reads and evaluates all JSONPath expressions defined in the given {@link PropertyDescriptor}
   * against the provided submission JSON node.
   *
   * <p>For each JSONPath expression in the <code>propertyDescriptor</code>, the corresponding
   * matches are retrieved using the <code>jsonPathReader</code>, and all matched results are
   * aggregated into a single list. Results are normalized to ensure uniqueness and preserve
   * insertion order.
   *
   * @param submission the root JSON node representing the submission data
   * @param propertyDescriptor the descriptor that holds JSONPath expressions to evaluate
   * @return a list of unique, trimmed string values in insertion order; an empty list if no matches
   *     are found or all values are null/empty
   */
  public List<String> read(JsonNode submission, PropertyDescriptor propertyDescriptor) {
    List<String> resultData = new ArrayList<>();
    List<String> jsonPaths = propertyDescriptor.getJsonPaths();

    for (String jsonPath : jsonPaths) {
      try {
        List<String> partialResultData = jsonPathReader.read(submission, jsonPath);
        resultData.addAll(partialResultData);
      } catch (IllegalArgumentException | ParsingException ex) {
        logger.warn("JSONPath evaluation failed for expression {}: {}", jsonPath, ex.getMessage());
      }
    }

    return normalizeResults(resultData);
  }

  /**
   * Reads and evaluates multiple JSONPath expressions directly.
   * Convenience overload for simple scenarios.
   */
  public List<String> read(JsonNode submission, List<String> jsonPaths) {
    PropertyDescriptor tempDescriptor = PropertyDescriptor.builder()
        .jsonPaths(jsonPaths)
        .build();
    return read(submission, tempDescriptor);
  }

  /**
   * Normalizes a list of string values by trimming whitespace, removing null or empty values, and
   * eliminating duplicates while preserving insertion order.
   *
   * @param values the raw list of string values to normalize
   * @return a list of unique, trimmed, non-empty string values in insertion order
   */
  private List<String> normalizeResults(List<String> values) {
    return values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .distinct()
        .toList();
  }
}
