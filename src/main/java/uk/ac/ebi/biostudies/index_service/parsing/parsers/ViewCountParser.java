package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.util.SubmissionUtils;
import uk.ac.ebi.biostudies.index_service.view_counts.ViewCountLoader;

/**
 * Parser implementation that retrieves the view count frequency for a submission based on its
 * accession.
 *
 * <p>This parser uses the {@link SubmissionUtils} to extract the accession from the submission JSON
 * node, then obtains the corresponding view count from the globally accessible {@link
 * ViewCountLoader}'s map. If no view count is found for the accession, the parser returns "0".
 *
 * <p>The parsed result is returned as a string representation of the count and is never null.
 */
@Component("ViewCountParser")
public class ViewCountParser implements Parser {

  /**
   * Parses the view count frequency from the submission JSON node.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor the descriptor containing field metadata and parsing instructions
   *     (not used)
   * @param jsonPathService the JSONPath service for evaluating JSONPaths (not used)
   * @return the view count frequency as a string, or "0" if none found
   * @throws IllegalArgumentException if the submission is null or invalid
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    String accession = SubmissionUtils.getAccession(submission);
    Long freq = ViewCountLoader.getViewCountMap().get(accession);
    if (freq == null) {
      freq = 0L;
    }
    return freq.toString();
  }
}
