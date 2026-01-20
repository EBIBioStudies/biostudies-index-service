package uk.ac.ebi.biostudies.index_service.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.ac.ebi.biostudies.index_service.parsing.parsers.SimpleAttributeParser;

@SpringBootTest
public class SpringBeanBasedParserFactoryIntegrationTest {

  @Autowired private SpringBeanBasedParserFactory factory;

  @Autowired
  private SimpleAttributeParser simpleAttributeParser; // must be declared as a Spring bean

  @Test
  public void testCreateParser_returnsRealBean() {
    String beanName = "SimpleAttributeParser"; // must match the bean name

    Parser parserFromFactory = factory.createParser(beanName);

    assertNotNull(parserFromFactory);

    assertEquals(simpleAttributeParser.getClass(), parserFromFactory.getClass());

    assertSame(simpleAttributeParser, parserFromFactory);
  }
}
