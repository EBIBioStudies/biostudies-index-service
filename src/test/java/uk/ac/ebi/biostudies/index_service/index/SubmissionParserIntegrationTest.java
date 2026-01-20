package uk.ac.ebi.biostudies.index_service.index;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;
import uk.ac.ebi.biostudies.index_service.util.JsonFileLoader;

@Slf4j
@SpringBootTest
@Tag("integration")
class SubmissionParserIntegrationTest {

  private static final String PARSING_FILES_DIR = "data/parsing";

  private static final String BIOIMAGES_STUDY_INPUT =
      "BioImages_Study_S-BIAD225/S-BIAD2225_input.json";
  private static final String BIOIMAGES_STUDY_EXPECTED =
      "BioImages_Study_S-BIAD225/S-BIAD2225_expected_output.json";

  private static final String ARRAY_EXPRESS_STUDY_INPUT =
      "ArrayExpress_Study_E-MTAB-9697/E-MTAB-9697_input.json";
  private static final String ARRAY_EXPRESS_STUDY_EXPECTED =
      "ArrayExpress_Study_E-MTAB-9697/E-MTAB-9697_expected_output.json";

  private static final String BIOIMAGES_COLLECTION_INPUT =
      "BioImages_Collection/BioImages_input.json";
  private static final String BIOIMAGES_COLLECTION_EXPECTED =
      "BioImages_Collection/BioImages_expected_output.json";

  private static final String BIOIMODELS_STUDY_INPUT =
      "BioModels_Study_MODEL2506160001/MODEL2506160001_input.json";
  private static final String BIOIMODELS_STUDY_EXPECTED =
      "BioModels_Study_MODEL2506160001/MODEL2506160001_expected_output.json";

  @Autowired private SubmissionParser submissionParser;
  @Autowired private CollectionRegistryService collectionRegistryService;

  // Parsing study (bioImages) without collection-specific properties.
  @Test
  void shouldParseBioImagesStudy() throws IOException {
    runParsingTestScenario(BIOIMAGES_STUDY_INPUT, BIOIMAGES_STUDY_EXPECTED);
  }

  // Parsing study (bioModels) without collection-specific properties.
  @Test
  void shouldParseBioModelsStudy() throws IOException {
    runParsingTestScenario(BIOIMODELS_STUDY_INPUT, BIOIMODELS_STUDY_EXPECTED);
  }

  // Parsing study with collection-specific properties.
  @Test
  void shouldParseArrayExpressStudy() throws IOException {
    runParsingTestScenario(ARRAY_EXPRESS_STUDY_INPUT, ARRAY_EXPRESS_STUDY_EXPECTED);
  }

  // Parsing study with collection-specific properties.
  @Test
  void shouldParseBioImagesCollection() throws IOException {
    runParsingTestScenario(BIOIMAGES_COLLECTION_INPUT, BIOIMAGES_COLLECTION_EXPECTED);
  }

  void runParsingTestScenario(String inputPath, String expectedPath) throws IOException {
    CollectionRegistry registry = collectionRegistryService.loadRegistry();
    assertFalse(
        registry.getGlobalPropertyRegistry().isEmpty(),
        "GlobalPropertyRegistry should not be empty");

    JsonNode submission =
        JsonFileLoader.loadJsonFromResources(Paths.get(PARSING_FILES_DIR, inputPath).toString());
    Map<String, Object> result = submissionParser.parseSubmission(submission);

    assertNotNull(result, "Result map should not be null");

    Map<String, Object> expected =
        JsonFileLoader.loadExpectedMapFromResources(
            Paths.get(PARSING_FILES_DIR, expectedPath).toString());
    assertExpectedParsing(result, expected);
  }

  private String keySetDiff(Set<String> actual, Set<String> expected) {
    Set<String> missing = new HashSet<>(expected);
    missing.removeAll(actual);

    Set<String> unexpected = new HashSet<>(actual);
    unexpected.removeAll(expected);

    return String.format(
        "Missing: %s, Unexpected: %s (%d vs %d keys)",
        missing, unexpected, actual.size(), expected.size());
  }

  private void assertExpectedParsing(Map<String, Object> actual, Map<String, Object> expected) {

    if (!actual.keySet().equals(expected.keySet())) {
      log.error("Key mismatch: {}", keySetDiff(actual.keySet(), expected.keySet()));
      fail(keySetDiff(actual.keySet(), expected.keySet()));
    }

    for (Entry<String, Object> entry : expected.entrySet()) {
      String key = entry.getKey();
      if (!actual.containsKey(key)) {
        fail("Key " + key + " not found in " + actual);
      }
      Object actualValue = actual.get(key);
      Object expectedValue = expected.get(key);

      assertEquals(expectedValue, actualValue, "Value of {" + key + "} not as expected");
    }
  }

  @Test
  void shouldThrowExceptionWhenSubmissionIsNull() {
    // When/Then
    assertThrows(
        NullPointerException.class,
        () -> submissionParser.parseSubmission(null),
        "Should throw NullPointerException when submission is null");
  }
}
