package uk.ac.ebi.biostudies.index_service.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

public interface Parser {
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
  String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService);
}
