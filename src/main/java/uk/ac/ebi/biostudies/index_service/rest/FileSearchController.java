package uk.ac.ebi.biostudies.index_service.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.biostudies.index_service.exceptions.SubmissionNotAccessibleException;
import uk.ac.ebi.biostudies.index_service.model.FileDocumentField;
import uk.ac.ebi.biostudies.index_service.search.files.ColumnSpec;
import uk.ac.ebi.biostudies.index_service.search.files.FileMetadata;
import uk.ac.ebi.biostudies.index_service.search.files.FileSearchResult;
import uk.ac.ebi.biostudies.index_service.search.files.FileSearchService;

/**
 * REST controller for file metadata search within BioStudies submissions.
 *
 * <p>Supports DataTables server-side processing with pagination, filtering, and sorting. Delegates
 * Lucene queries to {@link FileSearchService}.
 */
@Slf4j
@Tag(name = "File Search", description = "Search files within BioStudies submissions")
@RestController
@RequestMapping("/api/v1/files")

public class FileSearchController {

  private static final String DRAW = "draw";
  private static final String RECORDS_TOTAL = "recordsTotal";
  private static final String RECORDS_FILTERED = "recordsFiltered";
  private static final String DATA = "data";

  private final FileSearchService fileSearchService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  // A map for getting the field names as the UI expects them
  private static final Map<String, String> FIELD_MAP = Map.of(
      FileDocumentField.NAME.getName(), "Name",
      FileDocumentField.SIZE.getName(), "Size"
  );

  public FileSearchController(FileSearchService fileSearchService) {
    this.fileSearchService = fileSearchService;
  }

  /** Sanitizes Lucene field names for use as JSON keys. */
  private static String sanitizeFieldName(String name) {
    return name.replaceAll("[\\[\\]()\\s]", "_");
  }



  /**
   * Searches files for a submission using DataTables server-side parameters.
   *
   * @param accession submission accession
   * @param start 0-based pagination offset
   * @param pageSize results per page (-1 = all)
   * @param search global full-text search term
   * @param draw DataTables draw counter (echoed back)
   * @param metadata whether to include dynamic column metadata in response
   * @param columnsJson JSON array of column filters and sort directions
   * @param secretKey secret key for private submissions
   */
  @Operation(
      summary = "Search submission files",
      description = "DataTables-compatible file search with pagination, filtering, and sorting")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Success"),
    @ApiResponse(responseCode = "403", description = "Submission not accessible"),
    @ApiResponse(responseCode = "400", description = "Invalid columns JSON"),
    @ApiResponse(responseCode = "500", description = "Search failed")
  })
  @RequestMapping(
      value = "/search",
      method = {RequestMethod.GET, RequestMethod.POST},
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Map<String, Object>> searchFiles(
      @Parameter(description = "Submission accession") @RequestParam("accession") String accession,
      @Parameter(description = "0-based pagination offset")
          @RequestParam(name = "start", defaultValue = "0")
          int start,
      @Parameter(description = "Number of results per page (-1 = all)")
          @RequestParam(name = "pageSize", defaultValue = "10")
          int pageSize,
      @Parameter(description = "Global full-text search")
          @RequestParam(name = "search", required = false)
          String search,
      @Parameter(description = "DataTables draw counter (passthrough)")
          @RequestParam(name = "draw", required = false)
          Integer draw,
      @Parameter(description = "Include dynamic column metadata in response")
          @RequestParam(name = "metadata", defaultValue = "false")
          boolean metadata,
      @Parameter(
              description =
                  "JSON array of column filters/sorts, e.g. [{\"name\":\"size\",\"dir\":\"desc\"}]")
          @RequestParam(name = "columns", required = false)
          String columnsJson,
      @Parameter(description = "Secret key for private submissions")
          @RequestParam(name = "secretKey", required = false)
          String secretKey) {

    try {
      log.debug(
          "File search: accession={}, start={}, pageSize={}, metadata={}",
          accession,
          start,
          pageSize,
          metadata);

      List<ColumnSpec> columnSpecs = parseColumnSpecs(columnsJson);
      FileSearchResult result =
          fileSearchService.searchFiles(
              accession, start, pageSize, search, metadata, columnSpecs, secretKey);

      return ResponseEntity.ok(buildDataTablesResponse(result, draw, metadata));

    } catch (SubmissionNotAccessibleException e) {
      log.warn("Submission {} not accessible", accession);
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(Map.of("error", "Study not accessible"));
    } catch (IOException e) {
      log.error("Failed to parse columns JSON: {}", columnsJson, e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("error", "Invalid columns JSON"));
    } catch (Exception e) {
      log.error("File search failed for {}: {}", accession, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Search failed"));
    }
  }

  /**
   * Parses a JSON array string into a list of {@link ColumnSpec} instances.
   *
   * @param columnsJson JSON array of column descriptors
   * @return parsed column specs; empty list if input is null or blank
   * @throws IOException if the input is not a valid JSON array
   */
  private List<ColumnSpec> parseColumnSpecs(String columnsJson) throws IOException {
    if (columnsJson == null || columnsJson.trim().isEmpty()) {
      return List.of();
    }
    JsonNode node = objectMapper.readTree(columnsJson);
    if (!node.isArray()) {
      throw new IOException("columns parameter must be a JSON array");
    }
    List<ColumnSpec> specs = new ArrayList<>();
    for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
      JsonNode col = it.next();
      String name = col.hasNonNull("name") ? col.get("name").asText() : null;
      String value = col.hasNonNull("value") ? col.get("value").asText() : null;
      String dir = col.hasNonNull("dir") ? col.get("dir").asText() : null;
      if (name != null) {
        specs.add(new ColumnSpec(name, value, dir));
      }
    }
    return specs;
  }

  /**
   * Builds a DataTables-compatible response map from search results.
   *
   * @param result search result containing hits and file metadata
   * @param draw DataTables draw counter to echo back
   * @param metadata whether to include legacy dynamic fields (Name, Size, Description, etc.)
   */
  private Map<String, Object> buildDataTablesResponse(
      FileSearchResult result, Integer draw, boolean metadata) {

    Map<String, Object> response = new HashMap<>();
    response.put(DRAW, draw != null ? draw : 0);
    response.put(RECORDS_TOTAL, result.totalFiles());
    response.put(RECORDS_FILTERED, result.filteredFiles());

    List<Map<String, Object>> data = new ArrayList<>();

    for (FileMetadata fm : result.files()) {
      Map<String, Object> doc = new HashMap<>();

      // Core fields – always present
      doc.put("path", fm.path() != null ? fm.path() : "");
      doc.put("type", fm.type() != null ? fm.type() : "file");
      doc.put("size", fm.size()); // numeric size

      if (metadata && fm.metadata() != null && !fm.metadata().isEmpty()) {
        Map<String, String> m = fm.metadata();

//        // Legacy top-level fields mapped from index fields
//        // file_name -> Name
//        if (m.containsKey(FileDocumentField.NAME.getName())) {
//          doc.put("Name", m.get(FileDocumentField.NAME.getName()));
//        }
//        // file_size -> Size (string)
//        if (m.containsKey(FileDocumentField.SIZE.getName())) {
//          doc.put("Size", m.get(FileDocumentField.SIZE.getName()));
//        } else {
//          doc.put("Size", String.valueOf(fm.size()));
//        }

        // Copy any remaining metadata fields that are not already mapped, if you need them
        m.forEach(
            (k, v) -> {
              String normalizedKey = getFieldName(k);
              String safeKey = sanitizeFieldName(normalizedKey);
              if (!doc.containsKey(safeKey)) {
                doc.put(safeKey, v != null ? v : "");
              }
            });
      }

      data.add(doc);
    }

    response.put(DATA, data);
    return response;
  }

  private String getFieldName(String name) {
    return FIELD_MAP.getOrDefault(name, name);
  }
}
