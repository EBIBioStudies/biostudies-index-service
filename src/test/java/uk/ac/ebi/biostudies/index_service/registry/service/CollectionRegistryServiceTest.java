package uk.ac.ebi.biostudies.index_service.registry.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.biostudies.index_service.registry.loader.CollectionRegistryLoader;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

class CollectionRegistryServiceTest {
  private CollectionRegistryLoader loader;
  private CollectionRegistryValidator validator;
  private CollectionRegistryService service;

  @BeforeEach
  void setup() {
    loader = mock(CollectionRegistryLoader.class);
    validator = mock(CollectionRegistryValidator.class);
    service = new CollectionRegistryService(loader, validator);

    // Inject the config property manually (since @Value doesn't work outside Spring context)
    ReflectionTestUtils.setField(
        service, "collectionRegistryLocation", "classpath:collections.json");
  }

  @Test
  void testLoadRegistry_success() throws IOException {
    CollectionRegistry registry = mock(CollectionRegistry.class);

    when(loader.loadFromResource("classpath:collections.json")).thenReturn(registry);

    CollectionRegistry result = service.loadRegistry();

    verify(loader).loadFromResource("classpath:collections.json");
    verify(validator).validate(registry);

    assertSame(registry, result);
    assertSame(registry, service.getCurrentRegistry());
  }

  @Test
  void testLoadRegistry_loaderThrowsIOException() throws IOException {
    when(loader.loadFromResource(anyString())).thenThrow(new IllegalStateException("IO error"));

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.loadRegistry());

    assertEquals("IO error", ex.getMessage());
    assertNull(service.getCurrentRegistry());
    verify(validator, never()).validate(any());
  }

  @Test
  void testLoadRegistry_validatorThrowsException() throws IOException {
    CollectionRegistry registry = mock(CollectionRegistry.class);
    when(loader.loadFromResource(anyString())).thenReturn(registry);
    doThrow(new IllegalStateException("Validation failed")).when(validator).validate(registry);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.loadRegistry());

    assertEquals("Validation failed", ex.getMessage());
    // currentRegistry not set on failed validation
    assertNull(service.getCurrentRegistry());
  }

  @Test
  void testGetCurrentRegistry_initiallyNull() {
    assertNull(service.getCurrentRegistry());
  }

}
