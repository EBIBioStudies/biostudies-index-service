package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.util.SubmissionUtils;

@Component("ContentParser")
public class ContentParser implements Parser {

  // JSONPath that selects all "value" fields recursively inside "section".
  private static final String SECTION_VALUES = "$.section..value";

  // JSONPath that selects all "url" fields recursively inside "links".
  private static final String LINK_URLS = "$..links..url";

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
    // Manually assign the different paths to read to the PropertyDescriptor object

    Set<String> content = new LinkedHashSet<>();

    // Add accession value as it is
    String accession = SubmissionUtils.getAccession(submission);
    content.add(accession);

    // hack to make sure we are indexing PMC accessions in full text
    if (accession.startsWith("S-EPMC")) {
      content.add(getEMPCAccession(accession));
    }

    // Add Section values
    content.addAll(getSectionValues(submission, jsonPathService));

    // Add File names
    content.addAll(getFilesNames(submission, jsonPathService));

    // Add Link URLs
    content.addAll(getLinkUrls(submission, jsonPathService));

    return String.join(" ", content).trim();
  }

  private String getEMPCAccession(String accession) {
    // Leave only PMC...
    return accession.substring(3);
  }

  private List<String> getSectionValues(JsonNode submission, JsonPathService jsonPathService) {
    return jsonPathService.read(submission, SECTION_VALUES);
  }

  private List<String> getFilesNames(JsonNode submission, JsonPathService jsonPathService) {
    return SubmissionUtils.extractFilePaths(submission, jsonPathService);
  }

  private List<String> getLinkUrls(JsonNode submission, JsonPathService jsonPathService) {
    return jsonPathService.read(submission, LINK_URLS);
  }
}
