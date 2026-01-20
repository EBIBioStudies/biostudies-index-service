package uk.ac.ebi.biostudies.index_service.parsing;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enumerates all allowed Parser class names for use in the CollectionRegistry.
 * <p>
 * This enum is intended to provide compile-time type safety and clear documentation for all
 * supported Parser implementations in the data ingestion pipeline.
 * Each constant corresponds to a unique Parser, referenced by its class name.
 *
 * <p>
 * Usage example:
 * <pre>
 *   Set<String> allowed = Arrays.stream(ParserName.values())
 *                                .map(ParserName::getClassName)
 *                                .collect(Collectors.toSet());
 * </pre>
 */
public enum ParserName {

  /**
   * Default parser. Gets the value from an attribute matching a specific name.
   */
  SIMPLE_ATTRIBUTE("SimpleAttributeParser"),
  /**
   * Parser that counts and analyzes the number of nodes in a document.
   */
  NODE_COUNTING("NodeCountingParser"),

  /**
   * Parser specialized in EUToxRisk data type processing.
   */
  EU_TOX_RISK_DATA_TYPE("EUToxRiskDataTypeParser"),

  /**
   * Parser designed for handling access control or permissions metadata.
   */
  ACCESS("AccessParser"),

  /**
   * Parser that extracts and interprets type-related metadata.
   */
  TYPE("TypeParser"),

  /**
   * Parser utilizing JPath expressions to extract lists from structured data.
   */
  JPATH_LIST("JPathListParser"),

  /**
   * Parser that determines file format and type information.
   */
  FILE_TYPE("FileTypeParser"),

  /**
   * Parser that processes the core content of a document.
   */
  CONTENT("ContentParser"),

  /**
   * Parser that extracts creation time as milliseconds since epoch.
   */
  CREATION_TIME("CreationTimeParser"),

  /**
   * Parser that extracts modification time as milliseconds since epoch.
   */
  MODIFICATION_TIME("ModificationTimeParser"),

  /**
   * Parser that extracts modification year.
   */
  MODIFICATION_YEAR("ModificationYearParser"),

  /**
   * Parser that extracts release date as milliseconds since epoch.
   */
  RELEASE_TIME("ReleaseTimeParser"),

  /**
   * Parser that extracts release date.
   */
  RELEASE_DATE("ReleaseDateParser"),

  /**
   * Parser that extracts release year.
   */
  RELEASE_YEAR("ReleaseYearParser"),

  /**
   * Parser that represents a no-operation placeholder.
   */
  NULL("NullParser"),

  /**
   * Parser that extracts document view count metadata.
   */
  VIEW_COUNT("ViewCountParser"),

  /**
   * Parser that extracts and processes document titles.
   */
  TITLE("TitleParser");

  private final String className;

  ParserName(String className) {
    this.className = className;
  }

  public String getClassName() {
    return className;
  }

  public static Set<String> allowedParsers() {
    return Arrays.stream(values())
        .map(ParserName::getClassName)
        .collect(Collectors.toSet());
  }
}
