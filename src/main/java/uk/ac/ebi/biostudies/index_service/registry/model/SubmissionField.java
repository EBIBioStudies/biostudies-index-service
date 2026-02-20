package uk.ac.ebi.biostudies.index_service.registry.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum SubmissionField {
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

  /** Creation time */
  CREATION_TIME("ctime"),

  /** Modification time */
  MODIFICATION_TIME("mtime"),

  /** Facet link type */
  RELEASE_TIME("rtime"),

  /** secret key */
  SECRET_KEY("seckey"),

  /** Number of links */
  LINKS("links"),

  /** Number of files */
  FILES("files"),

  /** Content */
  CONTENT("content"),

  /** Storage mode */
  STORAGE_MODE("storageMode"),

  /** Number of views */
  VIEWS("views"),

  /** Is the submission public (already released)? */
  IS_PUBLIC("isPublic"),

  /** Collection membership */
  COLLECTION("collection");

  private final String name;

  SubmissionField(String name) {
    this.name = name;
  }

  /**
   * Returns the string representation used in the collection registry and Lucene index.
   */
  public String getName() {
    return name;
  }

  /**
   * Parses a string into its corresponding SubmissionFields enum.
   *
   * @param name the field name string
   * @return the matching SubmissionFields, or null if no match found
   */
  public static SubmissionField fromName(String name) {
    for (SubmissionField submissionField : values()) {
      if (submissionField.name.equalsIgnoreCase(name)) {
        return submissionField;
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
  public static Set<String> getAllSubmissionFieldNames() {
    return Arrays.stream(values())
        .map(SubmissionField::getName)
        .collect(Collectors.toSet());
  }
}
