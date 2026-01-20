package uk.ac.ebi.biostudies.index_service.analysis;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enumerates all allowed Analyzer class names for use in the CollectionRegistry.
 * <p>
 * This enum is intended to provide compile-time type safety and clear documentation for all
 * Lucene Analyzer implementations supported by the data indexing pipeline.
 * Each constant corresponds to a unique Analyzer, referenced by its class name.
 *
 * <p>
 * Usage example:
 * <pre>
 *   Set&lt;String&gt; allowed = Arrays.stream(AnalyzerName.values())
 *                                .map(AnalyzerName::getClassName)
 *                                .collect(Collectors.toSet());
 * </pre>
 */
public enum AnalyzerName {
  /**
   * Analyzer that processes attribute-type fields, typically used for metadata indexing.
   */
  ATTRIBUTE_FIELD("AttributeFieldAnalyzer"),

  /**
   * Analyzer specialized in access control or authorization-related fields.
   */
  ACCESS_FIELD("AccessFieldAnalyzer"),

  /**
   * Analyzer optimized for experimental text fields, suitable for full-text search and scientific descriptions.
   */
  EXPERIMENT_TEXT("ExperimentTextAnalyzer");

  private final String className;

  AnalyzerName(String className) {
    this.className = className;
  }

  public String getClassName() {
    return className;
  }

  public static Set<String> allowedAnalyzers() {
    return Arrays.stream(values())
        .map(AnalyzerName::getClassName)
        .collect(Collectors.toSet());
  }
}
