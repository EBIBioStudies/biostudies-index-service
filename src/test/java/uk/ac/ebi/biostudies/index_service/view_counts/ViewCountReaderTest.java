package uk.ac.ebi.biostudies.index_service.view_counts;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ViewCountReaderTest {

  private ViewCountReader reader;

  @BeforeEach
  void setUp() {
    reader = new ViewCountReader();
    // Inject test values for file path parts via reflection
    ReflectionTestUtils.setField(reader, "baseDir", "src/test/resources");
    ReflectionTestUtils.setField(reader, "updateDir", "data");
    ReflectionTestUtils.setField(reader, "submissionStatsFileName", "viewcounts.csv");
  }

  @Test
  void openViewCountFileReturnsBufferedReaderForExistingFile() throws IOException {
    // Ensure test file exists at src/test/resources/data/viewcounts.csv with some content for testing

    try (BufferedReader br = reader.openViewCountFile()) {
      assertNotNull(br);
      String firstLine = br.readLine();
      assertNotNull(firstLine);
    }
  }

  @Test
  void openViewCountFileThrowsIOExceptionForNonExistingFile() {
    // Point to a non-existing file and expect IOException
    ReflectionTestUtils.setField(reader, "submissionStatsFileName", "non-existent.csv");

    assertThrows(IOException.class, () -> reader.openViewCountFile());
  }
}
