package uk.ac.ebi.biostudies.index_service.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SpringBeanBasedAnalyzerFactory implements AnalyzerFactory {

  private final ApplicationContext context;

  public SpringBeanBasedAnalyzerFactory(ApplicationContext context) {
    this.context = context;
  }

  @Override
  public Analyzer createAnalyzer(String analyzerName) {
    if (analyzerName == null || analyzerName.isEmpty()) {
      throw new IllegalStateException("Analyzer name cannot be null or empty");
    }
    try {
      return context.getBean(analyzerName, Analyzer.class);
    } catch (BeansException e) {
      throw new IllegalStateException("No analyzer bean found for name: " + analyzerName, e);
    }
  }
}
