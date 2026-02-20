package uk.ac.ebi.biostudies.index_service.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.analysis.analyzers.AttributeFieldAnalyzer;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

/**
 * Manages field analyzers for text indexing and searching.
 *
 * <p>This class builds and provides a {@link PerFieldAnalyzerWrapper} using configurations supplied
 * via {@link PropertyDescriptor} objects. It supports thread-safe initialization and retrieval of
 * analyzers and expandable field metadata.
 *
 * <p>Intended as a singleton Spring-managed component initialized once at startup.
 */
@Slf4j
@Component
@Getter
public class AnalyzerManager {

  private final AnalyzerFactory analyzerFactory;

  // Thread-safe maps for analyzers and expandable fields
  private final Map<String, Analyzer> fieldAnalyzerMap = new ConcurrentHashMap<>();
  private final Set<String> expandableFieldNames = ConcurrentHashMap.newKeySet();
  private final AttributeFieldAnalyzer defaultAnalyzer;

  // Wrapper combining per-field analyzers with a fallback default analyzer
  private PerFieldAnalyzerWrapper perFieldAnalyzerWrapper;

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
   *
   * <p>This method is thread-safe and should be called only once during application startup.
   *
   * @param collectionRegistry the {@code CollectionRegistry} containing metadata about properties
   *     to index
   */
  public synchronized void init(CollectionRegistry collectionRegistry) {
    log.debug("Analyzers initialization started");

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

      try {
        Analyzer analyzer = analyzerFactory.createAnalyzer(analyzerName);
        fieldAnalyzerMap.put(fieldName, analyzer);
      } catch (IllegalStateException e) {
        log.error("Failed to create analyzer for field '{}': {}", fieldName, analyzerName, e);
      }

      // Track expandable fields
      if (Boolean.TRUE.equals(descriptor.getExpanded())) {
        expandableFieldNames.add(fieldName);
      }
    }

    // Create the wrapper with default analyzer (e.g. AttributeFieldAnalyzer) as fallback
    // Safe publication via synchronized method ensures visibility across threads
    this.perFieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(defaultAnalyzer, fieldAnalyzerMap);

    log.debug("{} properties with explicit analyzers", fieldAnalyzerMap.size());
  }

  /**
   * Get an unmodifiable set of expandable field names.
   *
   * @return unmodifiable set of expandable field names
   */
  public Set<String> getExpandableFieldNames() {
    return Collections.unmodifiableSet(expandableFieldNames);
  }

  /**
   * Returns the configured per-field analyzer wrapper.
   *
   * @return the PerFieldAnalyzerWrapper instance
   */
  public synchronized PerFieldAnalyzerWrapper getPerFieldAnalyzerWrapper() {
    return perFieldAnalyzerWrapper;
  }

  /**
   * Analyzes the given text using the same analyzer configuration as the index/search
   * for the specified field.
   *
   * @param fieldName the field name (e.g. "content")
   * @param text the text to analyze
   * @return list of token strings produced by the analyzer
   */
  public List<String> analyze(String fieldName, String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }

    PerFieldAnalyzerWrapper wrapper = perFieldAnalyzerWrapper;
    if (wrapper == null) {
      log.warn("Per-field analyzer wrapper is not initialized; returning empty token list");
      return List.of();
    }

    Analyzer analyzer;
    try {
      // Use wrapper.tokenStream() - it internally resolves the analyzer for the field
      analyzer = fieldAnalyzerMap.getOrDefault(fieldName, defaultAnalyzer);
    } catch (Exception e) {
      // Fallback to default if wrapper.getAnalyzer() fails
      analyzer = defaultAnalyzer;
    }

    if (analyzer == null) {
      log.warn("No analyzer found for field {}, using default", fieldName);
      analyzer = defaultAnalyzer;
    }

    List<String> tokens = new ArrayList<>();

    try (TokenStream ts = analyzer.tokenStream(fieldName, text)) {
      CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        tokens.add(termAttr.toString());
      }
      ts.end();
    } catch (IOException e) {
      log.warn("Error analyzing text for field {}: {}", fieldName, text, e);
    }

    return tokens;
  }

}
