package uk.ac.ebi.biostudies.index_service.view_counts;

import java.io.BufferedReader;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Component responsible for loading view count data from a file and maintaining
 * a thread-safe map of accession identifiers to their view counts.
 *
 * <p>This class delegates file access to {@link ViewCountReader} and parses the file line by line,
 * expecting CSV format with accession and count. It handles malformed lines and parsing errors
 * by logging warnings but continues processing the full file.</p>
 *
 * <p>Uses a static {@link ConcurrentHashMap} to store view counts to allow global access and
 * thread-safe concurrent reads and updates throughout the application lifecycle.</p>
 */
@Component
public class ViewCountLoader {

  private static final Logger LOGGER = LogManager.getLogger(ViewCountLoader.class);

  /** Thread-safe map storing accession to view count */
  private static final Map<String, Long> ACCESSION_VIEW_COUNT_MAP = new ConcurrentHashMap<>();

  private final ViewCountReader viewCountReader;

  /**
   * Constructs the loader with the given view count reader dependency.
   *
   * @param viewCountReader the component responsible for opening the view count file
   */
  public ViewCountLoader(ViewCountReader viewCountReader) {
    this.viewCountReader = viewCountReader;
  }

  /**
   * Returns the current global thread-safe map of accession to view count.
   *
   * @return map of accession IDs to their view counts
   */
  public static Map<String, Long> getViewCountMap() {
    return Collections.unmodifiableMap(ACCESSION_VIEW_COUNT_MAP);
  }

  /**
   * Clears the global view count map, removing all entries.
   */
  public static void unloadViewCountMap() {
    ACCESSION_VIEW_COUNT_MAP.clear();
  }

  /**
   * Loads view count data from the file provided by {@link ViewCountReader}, parsing each line
   * as CSV with 'accession,count'. Malformed lines or parsing errors are logged as warnings.
   *
   * <p>The method updates the global view count map with parsed values, replacing existing counts.
   * On failure to open or read the file, an error is logged and the method completes without throwing.</p>
   */
  public void loadViewCountFile() {
    String accession = "";
    try (BufferedReader reader = viewCountReader.openViewCountFile()) {
      String line;
      while ((line = reader.readLine()) != null) {
        try {
          String[] tokens = line.split(",");
          if (tokens.length >= 2) {
            accession = tokens[0].trim();
            Long count = Long.valueOf(tokens[1].trim());
            ACCESSION_VIEW_COUNT_MAP.put(accession, count);
          } else {
            LOGGER.warn("Malformed line (expected 2 tokens) in view count file: {}", line);
          }
        } catch (Exception ex) {
          LOGGER.warn("Problem parsing view stats for accession '{}': {}", accession, ex.getMessage());
        }
      }
    } catch (Exception ex) {
      LOGGER.error("Problem reading view count file:", ex);
    }
  }
}
