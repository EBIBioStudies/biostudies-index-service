package uk.ac.ebi.biostudies.index_service.parsing;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SpringBeanBasedParserFactory implements ParserFactory {

  private final ApplicationContext context;

  public SpringBeanBasedParserFactory(ApplicationContext context) {
    this.context = context;
  }

  /**
   * Creates a Parser instance by its simple class name. The full class name is constructed by
   * prefixing the base package.
   *
   * @param parserName the simple class name of the Parser to create.
   * @return the instantiated Parser
   * @throws IllegalStateException if the {@code parserName} is null or empty, or if the class
   *     cannot be found or instantiated.
   */
  @Override
  public Parser createParser(String parserName) {
    if (parserName == null || parserName.isEmpty()) {
      throw new IllegalStateException("Analyzer name cannot be null or empty");
    }
    try {
      return context.getBean(parserName, Parser.class);
    } catch (BeansException e) {
      throw new IllegalStateException("No parser bean found for name: " + parserName, e);
    }
  }
}
