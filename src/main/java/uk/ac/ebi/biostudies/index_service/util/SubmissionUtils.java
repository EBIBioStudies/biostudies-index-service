package uk.ac.ebi.biostudies.index_service.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;

/** Utility class containing helper methods for working with submission JsonNode objects. */
public class SubmissionUtils {

  private static final List<String> FILE_PATHS = Arrays.asList(
      "$..files[*].filePath",
      "$..files[*].path",
      "$..sections..files..filePath",
      "$..sections..files..path",
      "$.section.files[*][*].filePath",
      "$.section.files[*][*].path"
  );

  private static final List<String> FILE_TYPES = Arrays.asList(
      "$..files[*][?(@.type==\"file\")].type",
      "$..sections..files..[?(@.type==\"file\")].type",
      "$.section.files[*][*][?(@.type==\"file\")].type"
  );

  // Private constructor to prevent instantiation
  private SubmissionUtils() {
    throw new UnsupportedOperationException("Utility class should not be instantiated");
  }

  /**
   * Extracts the accession string from the given submission JSON node.
   *
   * <p>If the accession field is missing or empty, returns an empty string instead of null.
   *
   * @param submission the JsonNode representing a submission
   * @return the accession string, or empty string if not present
   * @throws IllegalArgumentException if submission is null
   */
  public static String getAccession(JsonNode submission) {
    if (submission == null) {
      throw new IllegalArgumentException("Submission JsonNode must not be null");
    }

    String accession = submission.path("accNo").asText("").trim(); // Official field first
    if (accession.isEmpty()) {
      accession = submission.path("accno").asText("").trim(); // Legacy field fallback
    }

    return accession;
  }

  // Extract file paths considering all known structures
  public static List<String> extractFilePaths(JsonNode submission, JsonPathService jsonPathService) {
    return jsonPathService.read(submission, FILE_PATHS);
  }

  // Extract file types with filters
  public static List<String> extractFileTypes(JsonNode submission, JsonPathService jsonPathService) {
    return jsonPathService.read(submission, FILE_TYPES);
  }

  public static String modifyRelativePathForPrivateStudies(String secretKey, String relativePath){
    if(secretKey==null || secretKey.length()<2 || relativePath.contains(".private"))
      return relativePath;
    return ".private/"+secretKey.substring(0, 2)+"/"+secretKey.substring(2)+"/"+relativePath;
  }
}
