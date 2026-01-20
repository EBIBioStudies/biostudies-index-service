package uk.ac.ebi.biostudies.index_service.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface JsonPathReader {
  /**
   * Executes the given JSONPath expression against the provided JSON node and returns a list of
   * matching values as strings.
   *
   * @throws IllegalArgumentException if jsonNode or jsonPathExpression are null or invalid
   * @throws ParsingException if JSONPath evaluation fails due to malformed expression or other
   *     errors
   */
  List<String> read(JsonNode jsonNode, String jsonPathExpression);
}
