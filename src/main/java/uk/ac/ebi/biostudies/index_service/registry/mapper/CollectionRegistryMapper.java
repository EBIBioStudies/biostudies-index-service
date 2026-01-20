package uk.ac.ebi.biostudies.index_service.registry.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.registry.dto.CollectionDescriptorDto;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

/**
 * The {@code CollectionRegistryMapper} is responsible for mapping JSON data into a {@link
 * CollectionRegistry} object. This class encapsulates the deserialization and transformation logic
 * necessary to convert raw JSON, which represents one or more collections with their property
 * descriptors, into corresponding domain objects.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * String jsonInput = ...;
 * CollectionRegistryMapper mapper = new CollectionRegistryMapper();
 * CollectionRegistry registry = mapper.fromJson(jsonInput);
 * }</pre>
 */
@Slf4j
@Component
public class CollectionRegistryMapper {

  private ObjectMapper objectMapper;

  /**
   * Initializes the CollectionRegistryMapper with a Jackson ObjectMapper configured to accept
   * case-insensitive enum values during JSON deserialization.
   *
   * <p>This configuration ensures that enum properties such as {@code FieldType} are deserialized
   * successfully regardless of the letter case in the input JSON string. For example, the JSON
   * value "facet" will correctly map to enum constant {@code FACET}.
   *
   * <p>Note that this setting affects only deserialization and requires that the {@link
   * MapperFeature#ACCEPT_CASE_INSENSITIVE_ENUMS} feature be enabled on the deserialization config
   * specifically.
   */
  public CollectionRegistryMapper() {
    objectMapper = new ObjectMapper();
    objectMapper =
        objectMapper.setConfig(
            objectMapper
                .getDeserializationConfig()
                .with(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS));
  }

  /**
   * Parses the given JSON string into a {@link CollectionRegistry} containing multiple {@link
   * CollectionDescriptor} instances built from the JSON structure.
   *
   * @param json JSON string representing the map of collectionName to property lists
   * @return {@link CollectionRegistry} with loaded collections and properties
   * @throws IllegalArgumentException if JSON parsing fails
   */
  public CollectionRegistry fromJson(String json) {
    String errorMessage;
    // String logDetail = String.format("JSON: %s", json);

    if (json == null || json.isBlank()) {
      errorMessage = "Null or empty json provided.";
      log.error(errorMessage);
      throw new IllegalArgumentException(errorMessage);
    }

    List<CollectionDescriptorDto> dtoList;
    try {
      dtoList = objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      errorMessage = "Failed to parse JSON array to collection registry.";
      log.error(errorMessage, e.getMessage());
      log.error(e.getOriginalMessage());
      throw new IllegalArgumentException(errorMessage);
    }

    if (dtoList == null || dtoList.isEmpty()) {
      errorMessage = "The parsed JSON produced empty data.";
      log.error(errorMessage);
      throw new IllegalArgumentException(errorMessage);
    }

    List<CollectionDescriptor> collections =
        dtoList.stream()
            .map(dto -> new CollectionDescriptor(dto.getCollectionName(), dto.getProperties()))
            .collect(Collectors.toList());

    return new CollectionRegistry(collections);
  }
}
