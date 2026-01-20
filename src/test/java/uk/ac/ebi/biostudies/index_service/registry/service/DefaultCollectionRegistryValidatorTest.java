package uk.ac.ebi.biostudies.index_service.registry.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.biostudies.index_service.CollectionRegistryTestDataFactory;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

class DefaultCollectionRegistryValidatorTest {
  private CollectionRegistryValidator validator;

  @BeforeEach
  void setUp() {
    validator = new DefaultCollectionRegistryValidator();
  }

  @Test
  void validate_withValidRegistry_doesNotThrow() {
    CollectionRegistry registry = CollectionRegistryTestDataFactory.createValidRegistry();
    assertDoesNotThrow(() -> validator.validate(registry));
  }

  @Test
  void validate_withInvalidJsonPath_throwsException() {
    CollectionRegistry registry = CollectionRegistryTestDataFactory.createRegistryWithInvalidJsonPath();
    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate(registry));
    assertTrue(ex.getMessage().contains("Invalid jsonPath"));
  }
}
