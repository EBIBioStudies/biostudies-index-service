package uk.ac.ebi.biostudies.index_service.registry.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
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
  void testLoadRegistry_success() {
    CollectionRegistry registry = mock(CollectionRegistry.class);
    // Mock the getGlobalPropertyRegistry for cache initialization
    when(registry.getGlobalPropertyRegistry()).thenReturn(Collections.emptyMap());
    when(registry.getCollections()).thenReturn(Collections.emptyList());
    when(registry.getCollectionDescriptor(anyString())).thenReturn(null);

    when(loader.loadFromResource("classpath:collections.json")).thenReturn(registry);

    CollectionRegistry result = service.loadRegistry();

    verify(loader).loadFromResource("classpath:collections.json");
    verify(validator).validate(registry);

    assertSame(registry, result);
    assertSame(registry, service.getCurrentRegistry());
  }

  @Test
  void testLoadRegistry_loaderThrowsException() {
    when(loader.loadFromResource(anyString())).thenThrow(new IllegalStateException("IO error"));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.loadRegistry());

    assertEquals("IO error", ex.getMessage());

    // Verify getCurrentRegistry throws IllegalStateException when registry not loaded
    IllegalStateException registryEx =
        assertThrows(IllegalStateException.class, () -> service.getCurrentRegistry());
    assertTrue(registryEx.getMessage().contains("Registry not initialized"));

    verify(validator, never()).validate(any());
  }

  @Test
  void testLoadRegistry_validatorThrowsException() {
    CollectionRegistry registry = mock(CollectionRegistry.class);
    when(registry.getGlobalPropertyRegistry()).thenReturn(Collections.emptyMap());
    when(registry.getCollections()).thenReturn(Collections.emptyList());

    when(loader.loadFromResource(anyString())).thenReturn(registry);
    doThrow(new IllegalStateException("Validation failed")).when(validator).validate(registry);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.loadRegistry());

    assertEquals("Validation failed", ex.getMessage());

    // currentRegistry not set on failed validation
    IllegalStateException registryEx =
        assertThrows(IllegalStateException.class, () -> service.getCurrentRegistry());
    assertTrue(registryEx.getMessage().contains("Registry not initialized"));
  }

  @Test
  void testGetCurrentRegistry_initiallyThrowsException() {
    // getCurrentRegistry should throw when registry not initialized
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.getCurrentRegistry());

    assertTrue(ex.getMessage().contains("Registry not initialized"));
    assertTrue(ex.getMessage().contains("call loadRegistry() first"));
  }

  @Test
  void testGetPropertyDescriptor_throwsWhenNotInitialized() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> service.getPropertyDescriptor("some.field"));

    assertTrue(ex.getMessage().contains("Registry not initialized"));
  }

  @Test
  void testGetPublicAndCollectionRelatedProperties_returnsEmptyWhenNotInitialized() {
    // This method returns empty list instead of throwing
    var result = service.getPublicAndCollectionRelatedProperties("someCollection");

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetPropertyDescriptor_afterSuccessfulLoad() {
    CollectionRegistry registry = mock(CollectionRegistry.class);

    when(registry.getGlobalPropertyRegistry()).thenReturn(Collections.emptyMap());
    when(registry.getCollections()).thenReturn(Collections.emptyList());
    when(registry.getCollectionDescriptor(anyString())).thenReturn(null);
    when(registry.getPropertyDescriptor("test.field")).thenReturn(null);
    when(loader.loadFromResource(anyString())).thenReturn(registry);

    service.loadRegistry();

    // Should not throw after successful load
    assertDoesNotThrow(() -> service.getPropertyDescriptor("test.field"));
    verify(registry).getPropertyDescriptor("test.field");
  }
}
