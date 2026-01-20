package uk.ac.ebi.biostudies.index_service.registry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * Data Transfer Object (DTO) used for JSON deserialization of property descriptors. This class is
 * mutable and includes a no-args constructor and setters required by JSON libraries such as Jackson
 * for deserialization.
 *
 * <p>After deserialization, instances of this class can be converted into immutable {@link
 * PropertyDescriptor} domain objects for use in business logic and indexing.
 *
 * <p>The fields correspond to JSON properties describing the indexing configuration of a single
 * property within a collection schema.
 *
 * <p>Example usage during deserialization:
 *
 * <pre>
 * ObjectMapper mapper = new ObjectMapper();
 * PropertyDescriptorDto dto = mapper.readValue(jsonString, PropertyDescriptorDto.class);
 * </pre>
 */
@Data
@NoArgsConstructor
public class PropertyDescriptorDto {
  /** Programmatic, unique name used to reference this property (e.g. "facet.organ"). */
  private String name;

  /** Human-friendly title for display (may be null). */
  private String title;

  /** Canonical field type used by the indexing system (e.g. "facet", "tokenized_string"). */
  @JsonProperty("fieldType")
  private FieldType fieldType;

  /** Optional analyzer name used for tokenization and indexing. */
  private String analyzer;

  /**
   * Optional list of JSONPath expressions used to extract values for this property from source JSON.
   * Each expression in the list is logically combined using OR during evaluation.
   */
  private List<String> jsonPaths;

  /** Whether the field is marked as sortable. Null means "unspecified". */
  private Boolean sortable;

  /** Whether the field supports multiple values. Null means "unspecified". */
  private Boolean multiValued;

  /** Optional facet subtype (for example "boolean"). */
  private String facetType;

  /** Whether "N/A" should be visible in facet UI for this property. Null means unspecified. */
  private Boolean naVisible;

  /** Name of the parser used to post-process values extracted for this property. */
  private String parser;

  /** Whether the field is explicitly retrieved or indexed. */
  private Boolean retrieved;

  /** Whether the field indexing should be expanded (e.g., tokenize fully). */
  private Boolean expanded;

  /**
   * Indicates if field values should be lowercased.
   */
  private Boolean toLowerCase;

  /** Default fallback value if not present in source JSON. */
  private String defaultValue;

  /** Hides field from public access or UI when true. */
  @JsonProperty("private")
  private Boolean isPrivate;

  /** Regular expression to filter or restrict matched values. */
  private String match;
}
