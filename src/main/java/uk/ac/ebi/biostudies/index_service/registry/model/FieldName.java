package uk.ac.ebi.biostudies.index_service.registry.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enum of commonly referenced field names from the collection registry.
 *
 * <p>These field names correspond to indexed fields in Lucene documents across all collections.
 * Not all registry fields are represented here - only those frequently referenced in code
 * to avoid magic strings and provide type safety.</p>
 */
public enum FieldName {

  /** Document id - acc */
  ID("id"),

  /** Submission type field (e.g., "Study", "Project") */
  TYPE("type"),

  /** Unique accession identifier */
  ACCESSION("accession"),

  /** Submission title */
  TITLE("title"),

  /** Author names */
  AUTHOR("author"),

  /** Access control field */
  ACCESS("access"),

  /** Release date */
  RELEASE_DATE("release_date"),

  /** Facet Collection */
  FACET_COLLECTION("facet.collection"),

  /** Facet file type */
  FACET_FILE_TYPE("facet.file_type"),

  /** Facet link type */
  FACET_LINK_TYPE("facet.link_type"),

  /** secret key */
  SECRET_KEY("seckey"),

  /** Collection membership */
  COLLECTION("collection");

  private final String name;

  FieldName(String name) {
    this.name = name;
  }

  /**
   * Returns the string representation used in the collection registry and Lucene index.
   */
  public String getName() {
    return name;
  }

  /**
   * Parses a string into its corresponding FieldName enum.
   *
   * @param name the field name string
   * @return the matching FieldName, or null if no match found
   */
  public static FieldName fromName(String name) {
    for (FieldName fieldName : values()) {
      if (fieldName.name.equalsIgnoreCase(name)) {
        return fieldName;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * Returns all allowed field names as a set.
   *
   * @return set of field name strings
   */
  public static Set<String> getAllFieldNames() {
    return Arrays.stream(values())
        .map(FieldName::getName)
        .collect(Collectors.toSet());
  }
}
