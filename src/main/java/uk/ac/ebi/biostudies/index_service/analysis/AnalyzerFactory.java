package uk.ac.ebi.biostudies.index_service.analysis;

import org.apache.lucene.analysis.Analyzer;

/** Factory interface for creating Analyzer instances by name. */
public interface AnalyzerFactory {
  /**
   * Creates an instance of an Analyzer based on the given analyzer name.
   *
   * @param analyzerName the simple name of the Analyzer class to instantiate. This name will be
   *     resolved within a configured package.
   * @return an Analyzer instance
   * @throws IllegalStateException if {@code analyzerName} is null, empty, or if instantiation
   *     fails.
   */
  Analyzer createAnalyzer(String analyzerName);
}
