package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * NullParser acts as a placeholder implementation of the {@link Parser} interface.
 *
 * <p>This parser does not perform any actual parsing or extraction from the submission JSON node.
 * Instead, it always returns null, indicating that no value was obtained or the field is intentionally
 * left unpopulated at this parsing stage.</p>
 *
 * <p>It can be used where a parser is required by the system architecture or pipeline but the value
 * is expected to be populated later by other processing steps or is explicitly absent.</p>
 */
@Component("NullParser")
public class NullParser implements Parser {

  /**
   * Returns null to indicate an intentionally empty or unpopulated parsed value.
   *
   * @param submission         the JSON node representing the submission to parse
   * @param propertyDescriptor the descriptor object containing field metadata and parsing instructions
   * @param jsonPathService    the service responsible for evaluating JSONPath expressions (unused)
   * @return null always, representing no parsed value
   * @throws IllegalArgumentException never thrown in this implementation
   */
  @Override
  public String parse(JsonNode submission, PropertyDescriptor propertyDescriptor,
      JsonPathService jsonPathService) {
    return null;
  }
}
