package uk.ac.ebi.biostudies.index_service.registry.mapper;

import uk.ac.ebi.biostudies.index_service.registry.dto.PropertyDescriptorDto;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * Utility class responsible for converting {@link PropertyDescriptorDto} instances, typically
 * created by JSON deserialization, into immutable {@link PropertyDescriptor} domain objects used in
 * business logic and indexing.
 *
 * <p>This separation ensures that the immutable {@link PropertyDescriptor} remains free from
 * dependencies on JSON libraries or serialization frameworks, promoting cleaner design and
 * testability.
 *
 * <p>The converter performs validation or normalization during the conversion, such as ensuring
 * mandatory fields are present and throwing descriptive exceptions otherwise.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * PropertyDescriptorDto dto = ...; // Deserialized from JSON
 * PropertyDescriptor descriptor = PropertyDescriptorConverter.fromDto(dto);
 * }</pre>
 *
 * <p>This class contains static methods only and is not intended to be instantiated.
 */
public class PropertyDescriptorConverter {

  private PropertyDescriptorConverter() {
    // Prevent instantiation
  }

  /**
   * Converts a mutable {@link PropertyDescriptorDto} into an immutable {@link PropertyDescriptor}.
   *
   * @param dto the DTO instance to convert; must not be null
   * @return the constructed immutable PropertyDescriptor
   * @throws IllegalArgumentException if required fields such as name or fieldType are missing
   */
  public static PropertyDescriptor fromDto(PropertyDescriptorDto dto) {
    // validate required fields if needed, then build immutable object
    if (dto.getName() == null || dto.getFieldType() == null) {
      throw new IllegalArgumentException("Name and fieldType must not be null");
    }

    return PropertyDescriptor.builder()
        .name(dto.getName())
        .title(dto.getTitle())
        .fieldType(dto.getFieldType())
        .analyzer(dto.getAnalyzer())
        .jsonPaths(dto.getJsonPaths())
        .sortable(dto.getSortable())
        .multiValued(dto.getMultiValued())
        .facetType(dto.getFacetType())
        .naVisible(dto.getNaVisible())
        .parser(dto.getParser())
        .retrieved(dto.getRetrieved())
        .expanded(dto.getExpanded())
        .toLowerCase(dto.getToLowerCase())
        .defaultValue(dto.getDefaultValue())
        .isPrivate(dto.getIsPrivate())
        .match(dto.getMatch())
        .build();
  }
}
