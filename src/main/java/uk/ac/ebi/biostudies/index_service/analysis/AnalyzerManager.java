package uk.ac.ebi.biostudies.index_service.analysis;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.analysis.analyzers.AttributeFieldAnalyzer;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * Manages field analyzers for text indexing and searching.
 * <p>
 * This class builds and provides a {@link PerFieldAnalyzerWrapper} using configurations supplied
 * via {@link PropertyDescriptor} objects. It supports thread-safe initialization and retrieval
 * of analyzers and expandable field metadata.
 * </p>
 *
 * <p>Intended as a singleton Spring-managed component initialized once at startup.</p>
 */
@Component
@Getter
public class AnalyzerManager {
  private static final Logger logger = LogManager.getLogger(AnalyzerManager.class);

  private final AnalyzerFactory analyzerFactory;

  // Thread-safe maps for analyzers and expandable fields
  private final Map<String, Analyzer> fieldAnalyzerMap = new ConcurrentHashMap<>();
  private final Set<String> expandableFieldNames = ConcurrentHashMap.newKeySet();
  private final AttributeFieldAnalyzer defaultAnalyzer;
  // Wrapper combining per-field analyzers with a fallback default analyzer
  private volatile PerFieldAnalyzerWrapper perFieldAnalyzerWrapper;

  /**
   * Creates a new AnalyzerManager.
   *
   * @param analyzerFactory the factory used to create Analyzer instances
   * @param defaultAnalyzer the default analyzer to use when no field-specific analyzer is defined
   */
  public AnalyzerManager(AnalyzerFactory analyzerFactory, AttributeFieldAnalyzer defaultAnalyzer) {
    this.analyzerFactory = analyzerFactory;
    this.defaultAnalyzer = defaultAnalyzer;
  }

  /**
   * Initializes field analyzers and expandable field names from the provided configuration map.
   * <p>
   * This method is thread-safe and should be called only once during application startup.
   * </p>
   *
   * @param collectionRegistry the {@code CollectionRegistry} containing metadata about properties to index
   */
  public synchronized void init(CollectionRegistry collectionRegistry) {
    logger.info("Analyzers initialization started");

    fieldAnalyzerMap.clear();
    expandableFieldNames.clear();

    Map<String, PropertyDescriptor> propertyDescriptorMap =
        collectionRegistry.getGlobalPropertyRegistry();

    for (Map.Entry<String, PropertyDescriptor> entry : propertyDescriptorMap.entrySet()) {
      String fieldName = entry.getKey();
      PropertyDescriptor descriptor = entry.getValue();

      if (descriptor.isFacet()) {
        // Skip facet fields as per original logic
        continue;
      }

      // Create analyzer if requested
      String analyzerName = descriptor.getAnalyzer();
      if (analyzerName != null && !analyzerName.isEmpty()) {
        try {
          Analyzer analyzer = analyzerFactory.createAnalyzer(analyzerName);
          fieldAnalyzerMap.put(fieldName, analyzer);
        } catch (IllegalStateException e) {
          logger.error("Failed to create analyzer for field '{}': {}", fieldName, analyzerName, e);
        }
      }

      // Track expandable fields
      if (Boolean.TRUE.equals(descriptor.getExpanded())) {
        expandableFieldNames.add(fieldName);
      }
    }

    // Create the wrapper with default analyzer (e.g. AttributeFieldAnalyzer) as fallback
    perFieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(defaultAnalyzer, fieldAnalyzerMap);

    logger.info("{} properties with explicit analyzers", fieldAnalyzerMap.size());
  }

  /**
   * Get an unmodifiable set of expandable field names.
   *
   * @return unmodifiable set of expandable field names
   */
  public Set<String> getExpandableFieldNames() {
    return Collections.unmodifiableSet(expandableFieldNames);
  }

}
