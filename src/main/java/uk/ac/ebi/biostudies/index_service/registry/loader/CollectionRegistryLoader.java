package uk.ac.ebi.biostudies.index_service.registry.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.registry.mapper.CollectionRegistryMapper;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

/**
 * Utility class for loading a {@link CollectionRegistry} from a JSON file located in the
 * application's resources folder. This class reads the JSON file as a string and delegates parsing
 * to the registry mapper, constructing a fully populated {@code CollectionRegistry} instance for
 * use in indexing or schema validation.
 *
 * <p>Typical usage involves passing a resource path (e.g. {@code
 * "/schema/collections-registry.json"}) and obtaining a registry object for downstream use:
 *
 * <pre>{@code
 * CollectionRegistryLoader loader = new CollectionRegistryLoader(new CollectionRegistryMapper());
 * CollectionRegistry registry = loader.loadFromResource("/schema/collections.json");
 * }</pre>
 *
 * <p>The loader automatically verifies resource existence and converts the contents to a UTF-8
 * string before parsing. If the resource is missing, unreadable, or the contents cannot be parsed,
 * an {@link IOException} or {@link IllegalArgumentException} is thrown.
 *
 * <p>Separation of concerns: This class isolates resource loading from JSON parsing and registry
 * construction, enabling testability and clear error handling.
 */
@Component
public class CollectionRegistryLoader {
  private final CollectionRegistryMapper registryMapper;
  private final ResourceLoader resourceLoader;

  private final Logger logger = LogManager.getLogger(CollectionRegistryLoader.class.getName());

  public CollectionRegistryLoader(
      CollectionRegistryMapper registryMapper, ResourceLoader resourceLoader) {
    this.registryMapper = registryMapper;
    this.resourceLoader = resourceLoader;
  }

  /**
   * Loads the collection registry from a JSON file in the resources.
   *
   * @param resourceLocation the path to the resource file (e.g., "/config/collections.json")
   * @return instantiated CollectionRegistry
   * @throws IllegalStateException if file cannot be read
   */
  public CollectionRegistry loadFromResource(String resourceLocation) {
    logger.debug("Loading collection registry from {}", resourceLocation);
    Resource resource = resourceLoader.getResource(resourceLocation);
    if (!resource.exists()) {
      throw new IllegalStateException("Resource not found: " + resourceLocation);
    }
    String json = null;
    try {
      json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return registryMapper.fromJson(json);
  }
}
