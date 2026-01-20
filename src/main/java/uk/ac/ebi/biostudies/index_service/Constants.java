package uk.ac.ebi.biostudies.index_service;

/**
 * A utility class holding application-wide constant values used across the index service.
 * This class is non-instantiable.
 */
public final class Constants {

  /**
   * Default placeholder value indicating that a field value was not available or not extracted from the JSON input.
   * Use this constant to represent missing or unavailable data consistently across the system.
   */
  public static final String NA = "n/a";

  // Project type. If a submission has this type, it will be parsed as "collection"
  public static final String PROJECT_TYPE = "project";

  // Official name for the project type
  public static final String COLLECTION_TYPE = "collection";
  /**
   * The delimiter character used to separate multiple values within a single facet field.
   * When parsing or joining facet values, this symbol denotes the boundary between individual values.
   */
  public static final String FACET_VALUE_DELIMITER = "|";
  /**
   * String contained in the {@code ACCESS_FIELD} property, indicating that the submission is public.
   */
  public static final String PUBLIC = "public";
  /**
   * JSON property name in a submission indicating the accession number.
   */
  public static final String ACCESSION_FIELD = "accNo";
  /**
   * JSON property name in a submission indicating if the submission has been released or not (true/false).
   */
  public static final String RELEASED_FIELD = "released";
  /**
   * JSON property name in a submission with information about access permissions.
   * If the submission is public, it will contain the string 'public'
   * Example "public username"
   */
  public static final String ACCESS_FIELD = "access";
  // JSON property name in a submission with the creation date
  public static final String CREATION_TIME_FIELD = "creationTime";
  // JSON property name in a submission with the modification date
  public static final String MODIFICATION_TIME_FIELD = "modificationTime";
  // JSON property name in a submission with the release date
  public static final String RELEASE_TIME_FIELD = "releaseTime";

  public static final String FILE_TYPE = "file";

  public static final String SECTION = "Section";

  public static final String ATTRIBUTE_NAME = "name";
  public static final String ATTRIBUTE_VALUE = "value";

  public static final String SECTIONS_WITH_FILES = "sections_with_files";
  public static final String FILE_ATT_KEY_VALUE = "fileAttKeyValue";
  public static final String FILE_ATTRIBUTE_NAMES  = "file_attribute_names";

  public static final String FILES = "files";
  public static final String HAS_FILE_PARSING_ERROR = "parsingError";

  public static final String CONTENT = "content";

  // Private constructor to prevent instantiation
  private Constants() {
    throw new UnsupportedOperationException("Constants class cannot be instantiated");
  }
}