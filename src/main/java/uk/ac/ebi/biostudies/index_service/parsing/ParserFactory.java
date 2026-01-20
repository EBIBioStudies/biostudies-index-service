package uk.ac.ebi.biostudies.index_service.parsing;


/** Factory interface for creating Parser instances by name. */
public interface ParserFactory {
  /**
   * Creates an instance of a Parser based on the given parser name.
   *
   * @param parserName the simple name of the Parser class to instantiate. This name will be
   *     resolved within a configured package.
   * @return a Parser instance
   * @throws IllegalStateException if {@code parserName} is null, empty, or if instantiation
   *     fails.
   */
  Parser createParser(String parserName);
}
