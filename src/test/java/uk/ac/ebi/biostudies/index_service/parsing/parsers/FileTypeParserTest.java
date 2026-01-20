package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JaywayJsonPathReader;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.util.SubmissionUtils;

class FileTypeParserTest {

  private FileTypeParser parser;
  private ObjectMapper mapper;
  private PropertyDescriptor propertyDescriptor;
  private JsonPathService jsonPathService;

  @BeforeEach
  void setUp() {
    parser = new FileTypeParser();
    mapper = new ObjectMapper();
    propertyDescriptor = PropertyDescriptor.builder().build();
    jsonPathService = new JsonPathService(new JaywayJsonPathReader());
  }

  @Test
  void parseBioImagesNestedSectionFilesLegacyPath() throws Exception {
    String json = """
      {
        "accno": "BioImages",
        "section": {
          "type": "Project",
          "files": [
            [
              {
                "path": "u/BioImages/logo.png",
                "size": 5306,
                "type": "file"
              }
            ]
          ]
        }
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("png", result);  // Extracts from legacy path in nested section.files[*][*].path
  }

  @Test
  void parseRootLevelFilesWithBothFilePathAndPath() throws Exception {
    String json = """
      {
        "files": [
          {"filePath": "direct/filepath.txt"},
          {"path": "direct/legacy.pdf"}
        ]
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("txt|pdf", result);  // Both extensions extracted, sorted by LinkedHashSet order
  }

  @Test
  void parseEmptyFileListReturnsEmptyString() throws Exception {
    String json = "{}";
    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);
    assertEquals("", result);
  }

  @Test
  void parseSingleFileWithKnownExtension() throws Exception {
    String json = """
      {
        "files": [{"filePath": "example.txt"}]
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);
    assertEquals("txt", result);
  }

  @Test
  void parseMultipleFilesWithDifferentExtensions() throws Exception {
    String json = """
      {
        "files": [
          {"filePath": "data.csv"},
          {"filePath": "report.pdf"},
          {"filePath": "image.png"}
        ]
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);
    System.out.println(result);

    // Check all extensions present (order may vary due to HashSet)
    assertTrue(result.contains("csv"));
    assertTrue(result.contains("pdf"));
    assertTrue(result.contains("png"));
    assertEquals(3, result.split("\\|").length);  // 3 unique types
  }

  @Test
  void parseMultipleFilesWithSameExtensionDeduplicated() throws Exception {
    String json = """
      {
        "files": [
          {"filePath": "a.txt"},
          {"filePath": "b.txt"},
          {"filePath": "c.txt"}
        ]
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);
    assertEquals("txt", result);  // Deduplicated by HashSet
  }

  @Test
  void parseFileWithoutExtensionIgnored() throws Exception {
    String json = """
      {
        "files": [{"filePath": "noextension"}]
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);
    assertEquals("", result);
  }

  @Test
  void parseFileEndingWithDotIgnored() throws Exception {
    String json = """
      {
        "files": [{"filePath": "strangefile."}]
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);
    assertEquals("", result);
  }

  @Test
  void parseHiddenFileStartingWithDotIgnored() throws Exception {
    String json = """
      {
        "files": [{"filePath": ".gitignore"}]
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);
    assertEquals("", result);
  }

  @Test
  void parseMixedNormalAndEdgeCaseFileNames() throws Exception {
    String json = """
      {
        "files": [
          {"filePath": "good.txt"},
          {"filePath": "noext"},
          {"filePath": "another.good.docx"},
          {"filePath": ".hiddenfile"},
          {"filePath": "file."}
        ]
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertTrue(result.contains("txt"));
    assertTrue(result.contains("docx"));
    assertEquals(2, result.split("\\|").length);  // Only 2 valid extensions
  }

  @Test
  void parseDeeplyNestedFilesVariousFormats() throws Exception {
    String json = """
      {
        "section": {
          "sections": [
            {
              "files": [
                [
                  {"filePath": "deep/nested/filepath.txt"},
                  {"path": "deep/nested/legacy.pdf"}
                ]
              ]
            }
          ]
        }
      }
      """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertTrue(result.contains("txt"));
    assertTrue(result.contains("pdf"));
  }
}
