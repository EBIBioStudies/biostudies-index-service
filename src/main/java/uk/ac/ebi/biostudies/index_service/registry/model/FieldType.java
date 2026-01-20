package uk.ac.ebi.biostudies.index_service.registry.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines the possible types of fields used during indexing.
 * Each type corresponds to a specific way the field is processed in the index.
 */
public enum FieldType {

  /**
   * Represents a field that is indexed as a single, untokenized string.
   * Typically used for exact matches (e.g., identifiers or codes).
   */
  UNTOKENIZED_STRING("untokenized_string"),

  /**
   * Represents a field that is tokenized (split into terms) during indexing.
   * Commonly used for full-text search fields.
   */
  TOKENIZED_STRING("tokenized_string"),

  /**
   * Represents a field used for long values.
   */
  LONG("long"),

  /**
   * Represents a field used for faceted search,
   * enabling aggregation and filtering based on distinct categories or values.
   */
  FACET("facet");

  private final String name;

  FieldType(String name) {
    this.name = name;
  }

  /**
   * Returns the string representation used in JSON configuration.
   */
  public String getName() {
    return name;
  }

  /**
   * Parses a string (e.g., from JSON) into its corresponding FieldType enum.
   * Returns null if no matching type is found.
   */
  public static FieldType fromName(String name) {
    for (FieldType type : values()) {
      if (type.name.equalsIgnoreCase(name)) {
        return type;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return name;
  }

  public static Set<String> allowedFieldTypes() {
    return Arrays.stream(values())
        .map(FieldType::getName)
        .collect(Collectors.toSet());
  }
}

