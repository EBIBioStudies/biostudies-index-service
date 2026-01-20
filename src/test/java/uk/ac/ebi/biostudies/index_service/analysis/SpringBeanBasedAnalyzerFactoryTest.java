package uk.ac.ebi.biostudies.index_service.analysis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.lucene.analysis.Analyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

public class SpringBeanBasedAnalyzerFactoryTest {

  private ApplicationContext mockContext;
  private SpringBeanBasedAnalyzerFactory factory;

  @BeforeEach
  public void setUp() {
    mockContext = mock(ApplicationContext.class);
    factory = new SpringBeanBasedAnalyzerFactory(mockContext);
  }

  @Test
  public void testCreateAnalyzer_validBeanName_returnsAnalyzer() {
    Analyzer mockAnalyzer = mock(Analyzer.class);
    when(mockContext.getBean("validAnalyzer", Analyzer.class)).thenReturn(mockAnalyzer);

    Analyzer result = factory.createAnalyzer("validAnalyzer");
    assertNotNull(result);
    assertSame(mockAnalyzer, result);
    verify(mockContext).getBean("validAnalyzer", Analyzer.class);
  }

  @Test
  public void testCreateAnalyzer_nullName_throwsException() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
      factory.createAnalyzer(null);
    });
    assertTrue(ex.getMessage().contains("cannot be null or empty"));
    verifyNoInteractions(mockContext);
  }

  @Test
  public void testCreateAnalyzer_emptyName_throwsException() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
      factory.createAnalyzer("");
    });
    assertTrue(ex.getMessage().contains("cannot be null or empty"));
    verifyNoInteractions(mockContext);
  }

  @Test
  public void testCreateAnalyzer_beanNotFound_throwsException() {
    when(mockContext.getBean("missingAnalyzer", Analyzer.class))
        .thenThrow(new BeansException("No bean") {});

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
      factory.createAnalyzer("missingAnalyzer");
    });
    assertTrue(ex.getMessage().contains("No analyzer bean found"));
  }
}
