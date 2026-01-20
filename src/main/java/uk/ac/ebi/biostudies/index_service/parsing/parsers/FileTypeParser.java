package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.util.SubmissionUtils;

/**
 * Parser implementation that extracts unique file type extensions from file paths within a
 * submission JSON.
 *
 * <p>This parser uses a JSONPath expression defined in the {@link PropertyDescriptor} to extract a
 * list of file path strings, then collects the unique file extensions found in those paths. The
 * result is returned as a single concatenated string with values separated by {@link
 * Constants#FACET_VALUE_DELIMITER}. Files without an extension or with a leading dot (hidden files)
 * are ignored.
 *
 * <p>This parser is intended for use cases where files are represented as JSON objects with a
 * path-like field (e.g., "filePath"), allowing downstream facets or filtering based on file types.
 */
@Component("FileTypeParser")
public class FileTypeParser implements Parser {

  /**
   * Parses the given submission JSON node using the provided property descriptor and JSONPath
   * service, extracting file type extensions from file paths.
   *
   * @param submission the JSON node representing the submission to parse
   * @param propertyDescriptor the descriptor object containing field metadata and JSONPath
   *     expressions
   * @param jsonPathService the service responsible for evaluating JSONPath expressions on the
   *     submission
   * @return the concatenated string of unique file extensions found, separated by {@link
   *     Constants#FACET_VALUE_DELIMITER}, or empty string if no valid file extensions are found
   * @throws IllegalArgumentException if any error occurs during JSONPath evaluation or parsing
   */
  @Override
  public String parse(
      JsonNode submission, PropertyDescriptor propertyDescriptor, JsonPathService jsonPathService) {
    // Evaluate the JSONPath to obtain file path strings from the submission
    List<String> fileNames = SubmissionUtils.extractFilePaths(submission, jsonPathService);

    // Extract unique file type extensions from these file paths
    Set<String> uniqueFileTypes = extractUniqueFileTypes(fileNames);

    // Join the set of unique extensions with the configured delimiter and return
    return String.join(Constants.FACET_VALUE_DELIMITER, uniqueFileTypes);
  }

  /**
   * Extracts the set of unique file type extensions from a list of file path strings.
   *
   * <p>Ignores file names that have no extension (no dot) or where the dot is the first character
   * (e.g. hidden files like ".gitignore").
   *
   * @param fileNames list of file path strings potentially containing extensions
   * @return a set of unique file extensions (without the dot)
   */
  private Set<String> extractUniqueFileTypes(List<String> fileNames) {
    Set<String> uniqueFileTypes = new HashSet<>();
    for (String fileName : fileNames) {
      int index = fileName.lastIndexOf(".");
      // Only consider extensions if dot is present and not the leading character
      if (index > 0) {
        String fileType = fileName.substring(index + 1);
        if (!fileType.isEmpty()) {
          uniqueFileTypes.add(fileType);
        }
      }
    }
    return uniqueFileTypes;
  }
}
