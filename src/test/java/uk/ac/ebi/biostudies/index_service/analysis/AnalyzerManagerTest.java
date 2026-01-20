package uk.ac.ebi.biostudies.index_service.analysis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.ac.ebi.biostudies.index_service.analysis.analyzers.AttributeFieldAnalyzer;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

class AnalyzerManagerTest {

  private AnalyzerFactory analyzerFactory;
  private AttributeFieldAnalyzer defaultAnalyzer;
  private AnalyzerManager analyzerManager;

  @BeforeEach
  void setup() {
    analyzerFactory = Mockito.mock(AnalyzerFactory.class);
    defaultAnalyzer = Mockito.mock(AttributeFieldAnalyzer.class);
    analyzerManager = new AnalyzerManager(analyzerFactory, defaultAnalyzer);
  }

  @Test
  void testConstructorAndInitialState() {
    assertNotNull(analyzerManager.getExpandableFieldNames());
    assertNull(analyzerManager.getPerFieldAnalyzerWrapper());
  }

  @Test
  void testInitPopulatesAnalyzersAndExpandableFields() {
    PropertyDescriptor desc1 = Mockito.mock(PropertyDescriptor.class);
    when(desc1.isFacet()).thenReturn(false);
    when(desc1.getAnalyzer()).thenReturn("custom");
    when(desc1.getExpanded()).thenReturn(true);

    PropertyDescriptor desc2 = Mockito.mock(PropertyDescriptor.class);
    when(desc2.isFacet()).thenReturn(false);
    when(desc2.getAnalyzer()).thenReturn(null);
    when(desc2.getExpanded()).thenReturn(false);

    Analyzer customAnalyzer = Mockito.mock(Analyzer.class);
    when(analyzerFactory.createAnalyzer("custom")).thenReturn(customAnalyzer);

    Map<String, PropertyDescriptor> map = new HashMap<>();
    map.put("field1", desc1);
    map.put("field2", desc2);

    CollectionRegistry collectionRegistry = Mockito.mock(CollectionRegistry.class);
    when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(map);

    analyzerManager.init(collectionRegistry);

    Set<String> expandables = analyzerManager.getExpandableFieldNames();
    assertTrue(expandables.contains("field1"));
    assertFalse(expandables.contains("field2"));

    PerFieldAnalyzerWrapper wrapper = analyzerManager.getPerFieldAnalyzerWrapper();
    assertNotNull(wrapper);
  }

  @Test
  void testInitSkipsFacetFields() {
    PropertyDescriptor desc = Mockito.mock(PropertyDescriptor.class);
    when(desc.isFacet()).thenReturn(true);

    Map<String, PropertyDescriptor> map = Map.of("facetField", desc);

    CollectionRegistry collectionRegistry = Mockito.mock(CollectionRegistry.class);
    when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(map);

    analyzerManager.init(collectionRegistry);

    assertTrue(analyzerManager.getExpandableFieldNames().isEmpty());
    assertNotNull(analyzerManager.getPerFieldAnalyzerWrapper());
  }

  @Test
  void testExpandableFieldsSetIsUnmodifiable() {
    PropertyDescriptor desc = Mockito.mock(PropertyDescriptor.class);
    when(desc.isFacet()).thenReturn(false);
    when(desc.getExpanded()).thenReturn(true);

    CollectionRegistry collectionRegistry = Mockito.mock(CollectionRegistry.class);
    when(collectionRegistry.getGlobalPropertyRegistry()).thenReturn(Map.of("expandField", desc));

    analyzerManager.init(collectionRegistry);

    Set<String> set = analyzerManager.getExpandableFieldNames();
    assertThrows(UnsupportedOperationException.class, () -> set.add("newField"));
  }
}
