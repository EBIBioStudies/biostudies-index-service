package uk.ac.ebi.biostudies.index_service.index;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.model.ExtendedFileProperty;
import uk.ac.ebi.biostudies.index_service.model.FileDocumentField;

/**
 * Factory for creating Lucene {@link Document} instances representing BioStudies files.
 *
 * <p>Maps extended submission file JSON into normalized Lucene documents with core metadata
 * (position, size, path, name, type) and dynamic attributes. Updates shared indexing state via
 * {@link FileIndexingContext} during attribute discovery [file:2].
 */
@Component
@RequiredArgsConstructor
public class FileDocumentFactory {

  /**
   * Builds a Lucene document for a single file and updates indexing context state.
   *
   * <p>Populates standard fields:
   *
   * <ul>
   *   <li>Position (DocValues + stored)
   *   <li>Size (DocValues + stored, defaults to 0)
   *   <li>Path/Name (multi-field indexed + DocValues + stored)
   *   <li>Type ("file") and directory flag
   *   <li>Section (if not study root)
   *   <li>Dynamic attributes (lowercase indexed + stored + DocValues)
   * </ul>
   *
   * <p>Side effects on context:
   *
   * <ul>
   *   <li>Adds discovered attribute names to {@code fileColumns}
   *   <li>Adds attribute name/value pairs to {@code searchableFileMetadata}
   * </ul>
   *
   * @param accession submission accession (used for EPMC type filtering)
   * @param context indexing context with mutable state collections
   * @param fileNode JSON metadata for this individual file
   * @param parentNode parent section/fileList containing this file
   * @return fully populated Lucene document ready for indexing
   */
  public Document buildFileDocument(
      String accession, FileIndexingContext context, JsonNode fileNode, JsonNode parentNode) {

    long position = context.getFileCounter().get();
    Document document = new Document();

    // Core positioning field (DocValues + stored)
    document.add(new NumericDocValuesField(FileDocumentField.POSITION.getName(), position));
    document.add(new StoredField(FileDocumentField.POSITION.getName(), position));

    // File size with fallback to 0
    long fileSize = extractFileSize(fileNode);
    document.add(new SortedNumericDocValuesField(FileDocumentField.SIZE.getName(), fileSize));
    document.add(new StoredField(FileDocumentField.SIZE.getName(), fileSize));

    // Normalized path and name with multi-field indexing
    String path = addNormalizedPath(document, fileNode);
    addNormalizedName(document, fileNode, path);

    // Standard file type and directory detection
    addFileTypeAndDirectory(document, fileNode);

    // Add owner of the document (the accession it is attached to)
    document.add(new StringField(FileDocumentField.OWNER.getName(), accession, Field.Store.YES));

    // Section field (non-study sections only)
    String section = addSectionName(document, parentNode, context.getFileColumns());
    if (section != null) {
      context.getFileColumns().add(Constants.SECTION);
    }

    // Dynamic file attributes
    addFileAttributes(document, accession, fileNode, context);

    return document;
  }

  /** Extracts file size from JSON node, defaulting to 0L for missing values. */
  private long extractFileSize(JsonNode fileNode) {
    if (fileNode.hasNonNull(ExtendedFileProperty.SIZE.getName())) {
      return Long.parseLong(fileNode.get(ExtendedFileProperty.SIZE.getName()).asText());
    }
    return 0L;
  }

  /**
   * Adds normalized path with multi-field indexing (StringField + StoredField + DocValues).
   *
   * @return resolved path or null if unavailable
   */
  private String addNormalizedPath(Document doc, JsonNode fileNode) {
    String path = resolvePath(fileNode);
    if (path == null || "null".equalsIgnoreCase(path)) {
      return null;
    }

    // Indexed (not stored) for exact matching/filtering
    doc.add(new StringField(FileDocumentField.PATH.getName(), path, Field.Store.NO));
    // Stored for retrieval
    doc.add(new StoredField(FileDocumentField.PATH.getName(), path));
    // DocValues for sorting/faceting [web:62][web:64]
    doc.add(new SortedDocValuesField(FileDocumentField.PATH.getName(), new BytesRef(path)));

    return path;
  }

  /** Resolves path preferring filePath → relPath fallback. */
  private String resolvePath(JsonNode fileNode) {
    if (fileNode.hasNonNull(ExtendedFileProperty.FILE_PATH.getName())) {
      return fileNode.get(ExtendedFileProperty.FILE_PATH.getName()).asText();
    }
    if (fileNode.hasNonNull(ExtendedFileProperty.REL_PATH.getName())) {
      return fileNode.get(ExtendedFileProperty.REL_PATH.getName()).asText();
    }
    return null;
  }

  /** Adds normalized filename with case-insensitive search field + exact + stored + DocValues. */
  private void addNormalizedName(Document doc, JsonNode fileNode, String path) {
    String name = resolveFileName(fileNode, path);
    if (name == null || name.isEmpty()) {
      return;
    }

    // Lowercase for case-insensitive search
    doc.add(new StringField(FileDocumentField.NAME.getName(), name.toLowerCase(), Field.Store.NO));
    // Original casing for exact matching
    doc.add(new StringField(FileDocumentField.NAME.getName(), name, Field.Store.NO));
    // Stored for display
    doc.add(new StoredField(FileDocumentField.NAME.getName(), name));
    // DocValues for sorting/faceting [web:62][web:64]
    doc.add(new SortedDocValuesField(FileDocumentField.NAME.getName(), new BytesRef(name)));
  }

  /** Resolves filename preferring explicit field → path basename fallback. */
  private String resolveFileName(JsonNode fileNode, String path) {
    JsonNode nameNode = fileNode.get(ExtendedFileProperty.FILE_NAME.getName());
    String name =
        (nameNode == null || "null".equalsIgnoreCase(nameNode.asText())) ? null : nameNode.asText();

    if (name == null && path != null) {
      int lastSlash = path.lastIndexOf('/');
      name = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
    }
    return name;
  }

  /** Adds standard file type ("file") and directory detection flag. */
  private void addFileTypeAndDirectory(Document doc, JsonNode fileNode) {
    doc.add(
        new StringField(FileDocumentField.TYPE.getName(), Constants.FILE_TYPE, Field.Store.YES));

    boolean isDirectory =
        fileNode.hasNonNull(ExtendedFileProperty.TYPE.getName())
            && "directory"
                .equalsIgnoreCase(fileNode.get(ExtendedFileProperty.TYPE.getName()).asText());

    doc.add(
        new StringField(
            ExtendedFileProperty.IS_DIRECTORY.getName(),
            Boolean.toString(isDirectory),
            Field.Store.YES));
  }

  /**
   * Adds section name for non-study-root sections, updating columns collection.
   *
   * @return resolved section name or null
   */
  private String addSectionName(Document doc, JsonNode parent, Set<String> fileColumns) {
    if (!isNonStudyRootSection(parent)) {
      return null;
    }

    if (!parent.hasNonNull("accNo")) {
      return null;
    }

    String rawSection = parent.get("accNo").asText("");
    String section = rawSection.replaceAll("/", "").replaceAll(" ", "");

    if (StringUtils.isEmpty(section)) {
      return null;
    }

    // Lowercase indexed, original stored, DocValues
    doc.add(
        new StringField(
            FileDocumentField.SECTION.getName(), section.toLowerCase(), Field.Store.NO));
    doc.add(new StoredField(FileDocumentField.SECTION.getName(), section));
    doc.add(new SortedDocValuesField(FileDocumentField.SECTION.getName(), new BytesRef(section)));

    return section;
  }

  private boolean isNonStudyRootSection(JsonNode parent) {
    return parent.hasNonNull("accNo")
        && (!parent.has("type") || !"study".equalsIgnoreCase(parent.get("type").textValue()));
  }

  /** Indexes dynamic file attributes, skipping duplicates and EPMC "type" fields. */
  private void addFileAttributes(
      Document doc, String accession, JsonNode fileNode, FileIndexingContext context) {
    JsonNode attributesNode = fileNode.get(ExtendedFileProperty.ATTRIBUTES.getName());
    if (attributesNode == null || !attributesNode.isArray()) {
      return;
    }

    Set<String> fileColumns = context.getFileColumns();
    Set<String> searchableMetadata = context.getSearchableFileMetadata();

    for (JsonNode attribute : attributesNode) {
      String attrName = attribute.path(Constants.ATTRIBUTE_NAME).asText(null);
      String attrValue = attribute.path(Constants.ATTRIBUTE_VALUE).asText(null);

      if (!isValidAttribute(attrName, attrValue, accession, doc)) {
        continue;
      }

      // Multi-field indexing pattern
      doc.add(new StringField(attrName, attrValue.toLowerCase(), Field.Store.NO));
      doc.add(new StoredField(attrName, attrValue));
      doc.add(new SortedDocValuesField(attrName, new BytesRef(attrValue)));

      // Update context state
      fileColumns.add(attrName);
      searchableMetadata.add(attrName);
      searchableMetadata.add(attrValue);
    }
  }

  /** Validates attribute: non-empty, non-duplicate, EPMC type filtering. */
  private boolean isValidAttribute(
      String attrName, String attrValue, String accession, Document doc) {
    if (attrName == null || attrValue == null || attrName.isEmpty() || attrValue.isEmpty()) {
      return false;
    }
    if (doc.getField(attrName) != null) { // Skip duplicates
      return false;
    }
    if ("type".equalsIgnoreCase(attrName) && accession.toLowerCase().contains("epmc")) {
      return false;
    }
    return true;
  }
}
