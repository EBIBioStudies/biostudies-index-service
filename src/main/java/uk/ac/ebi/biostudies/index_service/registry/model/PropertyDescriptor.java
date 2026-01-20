package uk.ac.ebi.biostudies.index_service.registry.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * A descriptor object that defines how a single indexed field (property) is represented in a
 * collection schema. Instances of {@code PropertyDescriptor} describe the mapping and indexing
 * behaviour for one logical field and are intended to be immutable metadata objects used by
 * indexing, validation and UI generation code.
 *
 * <p>Each descriptor MUST have a non-null {@link #getName() name} (the unique programmatic key used
 * to reference the property) and a non-null {@link #getFieldType() fieldType} (the canonical type
 * used by the indexing system, e.g. "facet", "tokenized_string", "long", "untokenized_string"). The
 * human-friendly {@link #getTitle() title} is optional but recommended for display purposes.
 *
 * <p>Optional attributes:
 *
 * <ul>
 *   <li>{@code analyzer} -- name of the text analyzer to use for tokenized fields.
 *   <li>{@code jsonPaths} -- list of JSONPath expressions used to extract the field values from
 *       source JSON; Each expression in the list is logically combined using OR during evaluation.
 *   <li>{@code sortable} -- whether the field is marked as sortable in the index.
 *   <li>{@code multiValued} -- whether the field accepts multiple values.
 *   <li>{@code facetType} -- optional facet type (for example "boolean" or other subtypes).
 *   <li>{@code naVisible} -- whether an "N/A" option should be visible in facet UIs.
 *   <li>{@code parser} -- optional name of a parser to post-process the extracted value.
 *   <li>{@code retrieved} -- whether the field is explicitly retrieved or indexed.
 *   <li>{@code expanded} -- whether the field indexing should be expanded (e.g., tokenize fully).
 *   <li>{@code toLowerCase} -- indicates if field values should be lowercased.
 *   <li>{@code defaultValue} -- default fallback value if not present in source JSON.
 *   <li>{@code isPrivate} -- hides field from public access or UI when true.
 *   <li>{@code match} -- regular expression to filter or restrict matched values.
 * </ul>
 *
 * <p>Construction: prefer using the {@code builder()} to create instances. Implementations that
 * deserialize from JSON should either use a compatible builder adapter or rely on the library's
 * POJO binding. The class is intentionally designed to be immutable after construction to allow
 * safe sharing across threads.
 *
 * <p>Examples:
 *
 * <pre>{@code
 * PropertyDescriptor p = PropertyDescriptor.builder()
 *     .name("facet.idr.study_type")
 *     .title("Study Type")
 *     .fieldType("facet")
 *     .build();
 * }</pre>
 *
 * @see CollectionDescriptor
 */
@Builder
@AllArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class PropertyDescriptor {
  /**
   * Programmatic, unique name used to reference this property (e.g. "facet.organ"). This field is
   * required and must not be null or blank.
   */
  private final String name;

  /** Human-friendly title for display (may be null). */
  private final String title;

  /**
   * Canonical field type used by the indexing system (e.g. "facet", "tokenized_string"). This field
   * is required and must not be null or blank.
   */
  private final FieldType fieldType;

  /** Optional analyzer name used for tokenization and indexing. */
  private final String analyzer;

  /** Whether the field is marked as sortable. Null means "unspecified". */
  private final Boolean sortable;

  /** Whether the field supports multiple values. Null means "unspecified". */
  private final Boolean multiValued;

  /** Optional facet subtype (for example "boolean"). */
  private final String facetType;

  /** Whether "N/A" should be visible in facet UI for this property. Null means unspecified. */
  private final Boolean naVisible;

  /** Name of the parser used to post-process values extracted for this property. */
  private final String parser;

  /** Whether the field is explicitly retrieved or indexed */
  private final Boolean retrieved;

  /** Whether the field indexing should be expanded (e.g., tokenize fully) */
  private final Boolean expanded;

  /**
   * Indicates if field values should be lowercased
   */
  private final Boolean toLowerCase;

  /** Default fallback value if not present in source JSON */
  private final String defaultValue;

  /** Hides field from public access or UI when true */
  private final Boolean isPrivate;

  /** Regular expression to filter or restrict matched values */
  private final String match;

  /**
   * Optional list of JSONPath expressions used to extract values for this property from source
   * JSON. Each expression in the list is logically combined using OR during evaluation.
   */
  private List<String> jsonPaths;

  /** Returns true if the fieldType equals {@code FieldType.FACET}. */
  public boolean isFacet() {
    return FieldType.FACET.equals(fieldType);
  }

  /** Returns true if the match has content. */
  public boolean hasMatch() {
    return match != null && !match.isEmpty();
  }

  /** Returns true if sortable is explicitly set to true. */
  public boolean isSortable() {
    return Boolean.TRUE.equals(sortable);
  }

  /** Returns true if toLowerCase is explicitly set to true. */
  public boolean isToLowerCase() {
    return Boolean.TRUE.equals(toLowerCase);
  }

  /** Returns true if jsonPath is not null and not blank. */
  public boolean hasJsonPaths() {
    return jsonPaths != null && !jsonPaths.isEmpty();
  }

}
