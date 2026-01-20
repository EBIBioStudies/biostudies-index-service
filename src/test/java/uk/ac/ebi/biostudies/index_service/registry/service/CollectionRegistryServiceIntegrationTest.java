package uk.ac.ebi.biostudies.index_service.registry.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

@SpringBootTest
@Tag("integration")
public class CollectionRegistryServiceIntegrationTest {
  @Autowired private CollectionRegistryService collectionRegistryService;

  @Test
  void loadRegistry_realJsonFile_success() throws IOException {
    CollectionRegistry registry = collectionRegistryService.loadRegistry();
    assertNotNull(registry, "The loaded registry should not be null");
    assertFalse(registry.getCollections().isEmpty(), "The registry should contain collections");
    assertFalse(
        registry.getGlobalPropertyRegistry().isEmpty(),
        "The GlobalPropertyRegistry map should contain collections");
  }
}
