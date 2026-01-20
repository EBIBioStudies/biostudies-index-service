package uk.ac.ebi.biostudies.index_service.registry.loader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import uk.ac.ebi.biostudies.index_service.registry.mapper.CollectionRegistryMapper;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

class CollectionRegistryLoaderTest {

  @Test
  void loadFromResource_returnsValidRegistry() throws IOException {
    CollectionRegistryMapper mapper = mock(CollectionRegistryMapper.class);
    ResourceLoader resourceLoader = mock(ResourceLoader.class);
    Resource resource = mock(Resource.class);

    String resourceLocation = "file:/schema/collections-registry.json";

    String jsonContent = "[{\"collectionName\":\"c1\",\"properties\":[]}]";

    when(resourceLoader.getResource(resourceLocation)).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8)));
    when(mapper.fromJson(jsonContent)).thenReturn(new CollectionRegistry(null)); // dummy return

    CollectionRegistryLoader loader = new CollectionRegistryLoader(mapper, resourceLoader);
    CollectionRegistry registry = loader.loadFromResource(resourceLocation);

    assertNotNull(registry);
    verify(mapper).fromJson(jsonContent);
  }
}
