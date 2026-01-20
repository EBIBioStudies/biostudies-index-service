package uk.ac.ebi.biostudies.index_service.parsing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

public class SpringBeanBasedParserFactoryTest {

  private ApplicationContext mockContext;
  private SpringBeanBasedParserFactory factory;

  @BeforeEach
  public void setUp() {
    mockContext = mock(ApplicationContext.class);
    factory = new SpringBeanBasedParserFactory(mockContext);
  }

  @Test
  public void testCreateParser_validBeanName_returnsParser() {
    Parser mockParser = mock(Parser.class);
    when(mockContext.getBean("validParser", Parser.class)).thenReturn(mockParser);

    Parser result = factory.createParser("validParser");

    assertNotNull(result);
    assertSame(mockParser, result);
    verify(mockContext).getBean("validParser", Parser.class);
  }

  @Test
  public void testCreateParser_nullName_throwsException() {
    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
      factory.createParser(null);
    });
    assertTrue(exception.getMessage().contains("cannot be null or empty"));
    verifyNoInteractions(mockContext);
  }

  @Test
  public void testCreateParser_emptyName_throwsException() {
    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
      factory.createParser("");
    });
    assertTrue(exception.getMessage().contains("cannot be null or empty"));
    verifyNoInteractions(mockContext);
  }

  @Test
  public void testCreateParser_beanNotFound_throwsException() {
    when(mockContext.getBean("missingParser", Parser.class))
        .thenThrow(new BeansException("No bean") {});

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
      factory.createParser("missingParser");
    });
    assertTrue(exception.getMessage().contains("No parser bean found"));
  }
}
