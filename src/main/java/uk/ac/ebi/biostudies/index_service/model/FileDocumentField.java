package uk.ac.ebi.biostudies.index_service.model;

/**
 * Enumerates the Lucene document field names used for file indexing.
 *
 * <p>Each enum constant represents a field in the file {@link org.apache.lucene.document.Document}
 * and exposes its underlying string name via {@link #getName()}.
 */
public enum FileDocumentField {

  /**
   * Document identifier (file name + position)
   */
  ID("id"),

  /**
   * Position of the file within the submission, used for ordering.
   */
  POSITION("file_position"),

  /**
   * Full path of the file (e.g. relative path in the submission).
   */
  PATH("file_path"),

  /**
   * Name of the file (usually the last segment of the path).
   */
  NAME("file_name"),

  /**
   * Size of the file, typically in bytes.
   */
  SIZE("file_size"),

  /**
   * Section accession the file belongs to, when not global.
   */
  SECTION("file_section"),

  /**
   * File type, such as "file" or "directory".
   */
  TYPE("file_type"),

  /**
   * Flag indicating whether this entry represents a directory.
   */
  IS_DIRECTORY("file_isDirectory"),

  /**
   * Accession of the submission that owns the file.
   */
  OWNER("file_owner");

  private final String fieldName;

  FileDocumentField(String fieldName) {
    this.fieldName = fieldName;
  }

  /**
   * Returns the Lucene document field name associated with this enum constant.
   *
   * @return the underlying field name to use when adding fields to a document
   */
  public String getName() {
    return fieldName;
  }
}
