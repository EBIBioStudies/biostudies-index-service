package uk.ac.ebi.biostudies.index_service.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.lucene.analysis.Analyzer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.ac.ebi.biostudies.index_service.analysis.analyzers.AttributeFieldAnalyzer;

@SpringBootTest
public class SpringBeanBasedAnalyzerFactoryIntegrationTest {

  @Autowired private SpringBeanBasedAnalyzerFactory factory;

  @Autowired
  private AttributeFieldAnalyzer attributeFieldAnalyzer; // must be declared as a Spring bean

  @Test
  public void testCreateAnalyzer_returnsRealBean() {
    String beanName = "AttributeFieldAnalyzer"; // must match the bean name

    Analyzer analyzerFromFactory = factory.createAnalyzer(beanName);

    assertNotNull(analyzerFromFactory);

    assertEquals(attributeFieldAnalyzer.getClass(), analyzerFromFactory.getClass());

    assertSame(attributeFieldAnalyzer, analyzerFromFactory);
  }
}
