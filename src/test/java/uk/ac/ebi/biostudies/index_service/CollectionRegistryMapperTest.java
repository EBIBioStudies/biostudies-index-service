package uk.ac.ebi.biostudies.index_service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.registry.mapper.CollectionRegistryMapper;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

class CollectionRegistryMapperTest {

  private final CollectionRegistryMapper instance = new CollectionRegistryMapper();

  @BeforeEach
  void setUp() {}

  @AfterEach
  void tearDown() {}

  @Test
  public void givenNullJson_whenFromJson_thenThrowsIllegalArgumentException() {

    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> instance.fromJson(null),
        "Expected fromJson() to throw IllegalArgumentException for invalid JSON"
    );

    assertEquals("Null or empty json provided.", thrown.getMessage());
  }

  @Test
  public void givenEmptyJsonString_whenFromJson_thenThrowsIllegalArgumentException() {

    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> instance.fromJson(""),
        "Expected fromJson() to throw IllegalArgumentException for invalid JSON"
    );

    assertEquals("Null or empty json provided.", thrown.getMessage());
  }

  @Test
  public void givenInvalidJson_whenFromJson_thenThrowsIllegalArgumentException() {

    String invalidJson = "invalid json";

    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> instance.fromJson(invalidJson),
        "Expected fromJson() to throw IllegalArgumentException for invalid JSON"
    );

    assertEquals("Failed to parse JSON array to collection registry.", thrown.getMessage());
  }

  @Test
  public void givenNonArrayJson_whenFromJson_thenThrowsIllegalArgumentException() {

    String nonArrayJson = "{}"; // JSON object passed, but expected is JSON array

    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> instance.fromJson(nonArrayJson),
        "Expected fromJson() to throw IllegalArgumentException for JSON object input"
    );

    assertEquals("Failed to parse JSON array to collection registry.", thrown.getMessage());
  }

  @Test
  public void givenEmptyArrayJson_whenFromJson_thenThrowsIllegalArgumentException() {

    String emptyArrayJson = "[]"; // JSON array passed, but it's empty

    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> instance.fromJson(emptyArrayJson),
        "Expected fromJson() to throw IllegalArgumentException for JSON object input"
    );

    assertEquals("The parsed JSON produced empty data.", thrown.getMessage());
  }

  @Test
  public void givenValidJsonArray_whenFromJson_thenProducesValidRegistry() {

    // JSON with one collection "c1" and one example property
    String json = """
        [
          {
            "collectionName": "c1",
            "properties": [
              {
                "name": "facet.c1.example",
                "title": "Example Property",
                "fieldType": "facet",
                "sortable": true
              }
            ]
          }
        ]
        """;

    CollectionRegistry registry = instance.fromJson(json);

    assertNotNull(registry);
    assertTrue(registry.containsCollection("c1"));

    CollectionDescriptor collection = registry.getCollectionDescriptor("c1");
    assertNotNull(collection);
    assertEquals("c1", collection.getCollectionName());

    List<PropertyDescriptor> properties = collection.getProperties();
    assertNotNull(properties);
    assertEquals(1, properties.size());

    PropertyDescriptor property = properties.getFirst();
    assertEquals("facet.c1.example", property.getName());
    assertEquals("Example Property", property.getTitle());
    assertEquals(FieldType.FACET, property.getFieldType());
    assertTrue(property.isSortable());
  }
}
