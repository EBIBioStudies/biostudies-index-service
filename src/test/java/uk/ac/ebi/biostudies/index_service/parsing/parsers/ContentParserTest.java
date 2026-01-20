package uk.ac.ebi.biostudies.index_service.parsing.parsers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.parsing.JaywayJsonPathReader;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

class ContentParserTest {

  private ContentParser parser;
  private ObjectMapper mapper;
  private PropertyDescriptor propertyDescriptor;
  private JsonPathService jsonPathService; // Use real or mock as needed

  @BeforeEach
  void setUp() {
    parser = new ContentParser();
    mapper = new ObjectMapper();
    propertyDescriptor = PropertyDescriptor.builder().build();
    jsonPathService = new JsonPathService(new JaywayJsonPathReader());
  }

  @Test
  void parseBioImagesNestedSectionFilesWithLegacyPath() throws Exception {
    String json =
        """
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

    assertTrue(result.contains("BioImages"));
    assertTrue(
        result.contains(
            "u/BioImages/logo.png")); // Must extract legacy path from section.files[*][*].path
  }

  @Test
  void parseRootLevelFilesWithBothFilePathAndLegacyPath() throws Exception {
    String json =
        """
    {
      "accno": "TEST001",
      "files": [
        {"filePath": "direct/filepath.txt", "path": "direct/legacy.txt"},
        {"path": "only-legacy.txt"}
      ]
    }
    """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertTrue(result.contains("direct/filepath.txt"));
    assertTrue(result.contains("direct/legacy.txt"));
    assertTrue(result.contains("only-legacy.txt"));
  }

  @Test
  void parseDeeplyNestedFilesInSections() throws Exception {
    String json =
        """
    {
      "accno": "NESTED001",
      "section": {
        "sections": [
          {
            "files": [
              [
                {"filePath": "deep/nested/filepath.txt"},
                {"path": "deep/nested/legacy.txt"}
              ]
            ]
          }
        ]
      }
    }
    """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertTrue(result.contains("deep/nested/filepath.txt"));
    assertTrue(
        result.contains("deep/nested/legacy.txt")); // Tests $..files[*].path recursive matching
  }

  @Test
  void parsePMCAccessionHackCorrectly() throws Exception {
    String json =
        """
    {
      "accno": "S-EPMC12345"
    }
    """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);
    System.out.println("result " + result);

    assertTrue(result.contains("S-EPMC12345"));
    assertTrue(result.contains("EPMC12345")); // Tests substring(3) logic
  }

  @Test
  void parseHandlesDuplicateValuesWithLinkedHashSet() throws Exception {
    String json =
        """
    {
      "accno": "DUPE001",
      "section": {
        "attributes": [{"value": "duplicate"}],
        "files": [{"path": "duplicate.txt"}]
      }
    }
    """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    // Verify no duplicates from different sources (LinkedHashSet removes them)
    long uniqueWords = result.split("\\s+").length;
    assertTrue(uniqueWords <= 3); // accno + 1 unique value from section/files
  }

  @Test
  void parseSectionValuesRecursiveExtraction() throws Exception {
    String json =
        """
    {
      "accno": "SEC001",
      "section": {
        "attributes": [{"value": "attribute value"}],
        "files": [{"attributes": [{"value": "file attribute"}]}],
        "description": "direct description value"
      }
    }
    """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertTrue(result.contains("attribute value"));
    assertTrue(result.contains("file attribute"));
    assertFalse(
        result.contains("direct description value")); // Should not contain this, no in `value`
  }

  @Test
  void parseLinksRecursiveExtraction() throws Exception {
    String json =
        """
    {
      "accno": "LINK001",
      "links": [
        {"url": "http://example.com"},
        {"links": [{"url": "http://nested.example.com"}]}
      ],
      "section": {
        "links": [{"url": "http://section.example.com"}]
      }
    }
    """;

    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertTrue(result.contains("http://example.com"));
    assertTrue(result.contains("http://nested.example.com"));
    assertTrue(result.contains("http://section.example.com")); // Tests $.links..url recursive
  }

  @Test
  void parseEmptyContentReturnsEmptyString() throws Exception {
    String json = "{}";
    JsonNode submission = mapper.readTree(json);
    String result = parser.parse(submission, propertyDescriptor, jsonPathService);

    assertEquals("", result.trim()); // Never null, empty string when no content
  }
}
