package uk.ac.ebi.biostudies.index_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.*;
import uk.ac.ebi.biostudies.index_service.view_counts.ViewCountLoader;
import uk.ac.ebi.biostudies.index_service.view_counts.ViewCountReader;

class ViewCountLoaderTest {

  private ViewCountLoader loader;
  private ViewCountReader mockViewCountReader;

  @BeforeEach
  void setUp() {
    mockViewCountReader = mock(ViewCountReader.class);
    loader = new ViewCountLoader(mockViewCountReader);
    ViewCountLoader.unloadViewCountMap();
  }

  @AfterEach
  void tearDown() {
    ViewCountLoader.unloadViewCountMap();
  }

  @Test
  void testLoadViewCountFilePopulatesMap() throws IOException {
    String content = "ACC001,10\nACC002,20\nACC003,30\n";
    BufferedReader reader = new BufferedReader(new StringReader(content));
    when(mockViewCountReader.openViewCountFile()).thenReturn(reader);

    loader.loadViewCountFile();

    Map<String, Long> viewCountMap = ViewCountLoader.getViewCountMap();
    assertEquals(3, viewCountMap.size());
    assertEquals(10L, viewCountMap.get("ACC001"));
    assertEquals(20L, viewCountMap.get("ACC002"));
    assertEquals(30L, viewCountMap.get("ACC003"));
  }

  @Test
  void testLoadViewCountFileSkipsMalformedLinesAndLogsWarnings() throws IOException {
    String content = "ACC001,10\nbadline\nACC002,notanumber\nACC003,30\n";
    BufferedReader reader = new BufferedReader(new StringReader(content));
    when(mockViewCountReader.openViewCountFile()).thenReturn(reader);

    loader.loadViewCountFile();

    Map<String, Long> viewCountMap = ViewCountLoader.getViewCountMap();
    assertEquals(2, viewCountMap.size());
    assertEquals(10L, viewCountMap.get("ACC001"));
    assertEquals(30L, viewCountMap.get("ACC003"));
  }

  @Test
  void testLoadViewCountFileHandlesIOExceptionWhenOpeningFile() throws IOException {
    when(mockViewCountReader.openViewCountFile()).thenThrow(new IOException("File not found"));

    // Should not throw but log error internally
    assertDoesNotThrow(() -> loader.loadViewCountFile());

    assertTrue(ViewCountLoader.getViewCountMap().isEmpty());
  }

  @Test
  void testUnloadViewCountMapClearsMap() throws IOException {
    String content = "ACC001,10\n";
    BufferedReader reader = new BufferedReader(new StringReader(content));
    when(mockViewCountReader.openViewCountFile()).thenReturn(reader);

    loader.loadViewCountFile();

    assertFalse(ViewCountLoader.getViewCountMap().isEmpty());

    ViewCountLoader.unloadViewCountMap();

    assertTrue(ViewCountLoader.getViewCountMap().isEmpty());
  }
}
