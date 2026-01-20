package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.parsing.ParsingException;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

@Component("TypeParser")
public class TypeParser implements Parser {

  // Json path to read the type value of a submission
  private static final String JSON_PATH = "$.section.type";

  private static final Logger logger = LogManager.getLogger(TypeParser.class.getName());

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
    String result = "";
    try {
      List<String> resultData = jsonPathService.read(submission, JSON_PATH);
      result = formatType(resultData);
    } catch (ParsingException e) {
      // Just logging, no need to propagate
      logger.error(e);
    }

    return result;
  }

  private String formatType(List<String> resultData) {
    String result = "";
    if (resultData == null || resultData.isEmpty()) {
      throw new ParsingException("Couldn't find a type for the submission");
    }
    if (resultData.size() > 1) {
      throw new ParsingException(
          String.format("Found more than one result data for type: %s", resultData));
    }
    result = resultData.getFirst();

    if (result != null) {
      result = result.toLowerCase();
    }
    result = Constants.PROJECT_TYPE.equals(result) ? Constants.COLLECTION_TYPE : result;
    return result;
  }
}
