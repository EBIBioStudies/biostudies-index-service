package uk.ac.ebi.biostudies.index_service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.registry.dto.PropertyDescriptorDto;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

class CollectionRegistryTest {

  @Test
  public void testConstructorWithNullList() {
    CollectionRegistry registry = new CollectionRegistry(null);
    assertNull(registry.getCollectionDescriptor("nonexistent"));
    assertFalse(registry.containsCollection("anything"));
    assertTrue(registry.getCollections().isEmpty());
  }

  @Test
  public void testConstructorWithEmptyList() {
    CollectionRegistry registry = new CollectionRegistry(List.of());
    assertTrue(registry.getCollections().isEmpty());
    assertFalse(registry.containsCollection("anything"));
    assertNull(registry.getCollectionDescriptor("any"));
  }

  @Test
  public void testGetCollectionDescriptorFound() {
    CollectionDescriptor desc1 = new CollectionDescriptor("c1", List.of());
    CollectionDescriptor desc2 = new CollectionDescriptor("c2", List.of());
    CollectionRegistry registry = new CollectionRegistry(List.of(desc1, desc2));

    assertEquals(desc1, registry.getCollectionDescriptor("c1"));
    assertEquals(desc2, registry.getCollectionDescriptor("c2"));
  }

  @Test
  public void testGetCollectionDescriptorNotFound() {
    CollectionDescriptor desc = new CollectionDescriptor("c1", List.of());
    CollectionRegistry registry = new CollectionRegistry(List.of(desc));

    assertNull(registry.getCollectionDescriptor("unknown"));
    assertNull(registry.getCollectionDescriptor(null));
  }

  @Test
  public void testContainsCollection() {
    CollectionDescriptor desc = new CollectionDescriptor("c1", List.of());
    CollectionRegistry registry = new CollectionRegistry(List.of(desc));

    assertTrue(registry.containsCollection("c1"));
    assertFalse(registry.containsCollection("c2"));
    assertFalse(registry.containsCollection(null));
  }

  @Test
  public void testCollectionsImmutability() {
    CollectionDescriptor desc = new CollectionDescriptor("c1", List.of());
    List<CollectionDescriptor> inputList = new ArrayList<>();
    inputList.add(desc);

    CollectionRegistry registry = new CollectionRegistry(inputList);
    // Attempt to modify input list after construction
    inputList.add(new CollectionDescriptor("c2", List.of()));

    // The registry should not reflect this modification
    assertFalse(registry.containsCollection("c2"));

    // The collections list obtained should be unmodifiable
    assertThrows(
        UnsupportedOperationException.class,
        () -> {
          registry.getCollections().add(new CollectionDescriptor("c3", List.of()));
        });
  }

  @Test
  public void testGetPropertyDescriptor_existingField_returnsDescriptor() {
    CollectionRegistry registry = createCollectionRegistry();

    PropertyDescriptor propDesc1 =
        PropertyDescriptor.builder().name("field1Name").fieldType(FieldType.FACET).build();
    PropertyDescriptor result = createCollectionRegistry().getPropertyDescriptor("field1Name");
    assertNotNull(result, "Should return PropertyDescriptor for existing field");
    assertEquals(propDesc1, result, "Returned PropertyDescriptor should match expected one");
  }

  @Test
  public void testGetPropertyDescriptor_nonExistentField_returnsNull() {
    PropertyDescriptor result = createCollectionRegistry().getPropertyDescriptor("nonexistent");
    assertNull(result, "Should return null for a non-existent field");
  }

  @Test
  public void testGetPropertyDescriptor_nullFieldName_returnsNull() {
    PropertyDescriptor result = createCollectionRegistry().getPropertyDescriptor(null);
    assertNull(result, "Should return null when given null field name");
  }

  private CollectionRegistry createCollectionRegistry() {
    PropertyDescriptorDto propDesc1 = new PropertyDescriptorDto();
    propDesc1.setName("field1Name");
    propDesc1.setFieldType(FieldType.FACET);
    PropertyDescriptorDto propDesc2 = new PropertyDescriptorDto();
    propDesc2.setName("field2Name");
    propDesc2.setFieldType(FieldType.FACET);

    CollectionDescriptor collectionDescriptor1 =
        new CollectionDescriptor("field1", List.of(propDesc1));
    CollectionDescriptor collectionDescriptor2 =
        new CollectionDescriptor("field2", List.of(propDesc2));

    return new CollectionRegistry(List.of(collectionDescriptor1, collectionDescriptor2));
  }
}
