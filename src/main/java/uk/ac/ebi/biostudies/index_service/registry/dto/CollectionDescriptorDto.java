package uk.ac.ebi.biostudies.index_service.registry.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a collection descriptor as defined in JSON input. This class is
 * mutable and intended for deserialization only.
 *
 * <p>It mirrors the JSON structure: [ { "collectionName": "c1", "properties": [ ... ] } ]
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionDescriptorDto {
  /** The name of the collection. */
  private String collectionName;

  /** List of PropertyDescriptorDto for properties in this collection. */
  private List<PropertyDescriptorDto> properties;
}
