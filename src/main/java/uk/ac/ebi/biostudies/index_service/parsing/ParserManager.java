package uk.ac.ebi.biostudies.index_service.parsing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

@Component
public class ParserManager {

  private static final Logger logger = LogManager.getLogger(ParserManager.class);
  private final ParserFactory parserFactory;
  // Thread-safe maps for parsers
  private final Map<String, Parser> fieldParserMap = new ConcurrentHashMap<>();

  /**
   * Creates a new ParserManager.
   *
   * @param parserFactory the factory used to create Parser instances
   */
  public ParserManager(ParserFactory parserFactory) {
    this.parserFactory = parserFactory;
  }

  /**
   * Initializes field parsers from the provided collectionRegistry.
   * <p>
   * This method is thread-safe and should be called only once during application startup.
   * It clears any existing parsers and recreates mappings based on the global property registry.
   * </p>
   * <p>
   * During parser creation via {@code parserFactory.createParser}, an
   * {@link IllegalStateException} may be thrown if the parser class name is invalid or
   * the creation fails. This exception is not caught here since it should already
   * be logged by the factory, and the caller is responsible for handling it if needed.
   * </p>
   *
   * @param collectionRegistry the {@code CollectionRegistry} containing metadata about properties to index
   * @throws IllegalStateException if parser creation fails for any property (propagated from parserFactory)
   */
  public synchronized void init(CollectionRegistry collectionRegistry) {
    logger.info("Parsers initialization started");
    fieldParserMap.clear();

    Map<String, PropertyDescriptor> propertyDescriptorMap =
        collectionRegistry.getGlobalPropertyRegistry();

    for (Map.Entry<String, PropertyDescriptor> entry : propertyDescriptorMap.entrySet()) {

      String parserClassName = findParserClassName(entry.getValue());

      Parser parser = parserFactory.createParser(parserClassName);
      fieldParserMap.put(entry.getKey(), parser);
    }
    logger.info("{} properties with explicit parsers", fieldParserMap.size());
  }

  /**
   * Determines the parser class name for the given property descriptor.
   *
   * <p>Returns the class name specified in the descriptor, or selects a default based on whether
   * the descriptor has JSON paths:
   *
   * <ul>
   *   <li>If {@code hasJsonPaths} is true, returns {@code ParserName.JPATH_LIST.getClassName()}.
   *   <li>Otherwise, returns {@code ParserName.SIMPLE_ATTRIBUTE.getClassName()}.
   * </ul>
   *
   * @param descriptor the property descriptor to examine
   * @return the parser class name to use for this property
   */
  private String findParserClassName(PropertyDescriptor descriptor) {
    String parserClassName = descriptor.getParser();

    if (parserClassName == null || parserClassName.isEmpty()) {
      parserClassName =
          descriptor.hasJsonPaths()
              ? ParserName.JPATH_LIST.getClassName()
              : ParserName.SIMPLE_ATTRIBUTE.getClassName();
    }

    return parserClassName;
  }

  /**
   * Retrieves the parser associated with the specified field.
   *
   * @param field the name of the field to get the parser for
   * @return the {@link Parser} instance associated with the field,
   *         or {@code null} if no parser is registered for the field
   */
  public Parser getParser(String field) {
    return fieldParserMap.get(field);
  }

}
