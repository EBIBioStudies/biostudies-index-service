package uk.ac.ebi.biostudies.index_service.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Concrete implementation of JsonPathService using Jayway JSONPath library.
 *
 * <p>This service evaluates JSONPath expressions against a Jackson JsonNode representing the input
 * JSON document. It uses Jayway JSONPath's ReadContext to execute queries and returns the matching
 * values as a list of strings.
 *
 * <p>The service encapsulates the Jayway JSONPath library usage, providing a simple interface for
 * JSONPath evaluation and hiding library-specific details from callers. It converts query results
 * to strings for consistent downstream processing.
 *
 * <p>Example usage:
 *
 * <pre>
 * JsonPathService service = new JaywayJsonPathService();
 * List&lt;String&gt; values = service.read(jsonNode, "$.section.attributes[?(@.name =~ /Title/i)].value");
 * </pre>
 */
@Component
public class JaywayJsonPathReader implements JsonPathReader {

  /**
   * Evaluates the given JSONPath expression against the provided JSON node.
   *
   * @param jsonNode the Jackson JsonNode representing the JSON document to query
   * @param jsonPathExpression the JSONPath expression string to evaluate
   * @return a list of string representations of all values matching the JSONPath expression, or an
   *     empty list if no matches found
   * @throws IllegalArgumentException if jsonNode or jsonPathExpression are null or invalid
   * @throws ParsingException if JSONPath evaluation fails due to malformed expression or other
   *     errors
   */
  @Override
  public List<String> read(JsonNode jsonNode, String jsonPathExpression) {

    if (jsonNode == null) {
      throw new IllegalArgumentException("jsonNode must not be null");
    }
    if (jsonPathExpression == null || jsonPathExpression.trim().isEmpty()) {
      throw new IllegalArgumentException("jsonPathExpression must not be null or empty");
    }

    try {
      ReadContext ctx = JsonPath.parse(jsonNode.toString());
      Object result = ctx.read(jsonPathExpression);

      if (result == null) {
        return List.of();
      }

      if (result instanceof List) {
        @SuppressWarnings("unchecked")
        List<Object> results = (List<Object>) result;
        return results.stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.toList());
      } else {
        return List.of(result.toString());
      }
    } catch (PathNotFoundException pnfe) {
      // Return empty list if the JSONPath does not match any element
      return List.of();
    } catch (Exception ex) {
      throw new ParsingException(
          "Failed to evaluate JSONPath expression: " + jsonPathExpression, ex);
    }
  }
}
