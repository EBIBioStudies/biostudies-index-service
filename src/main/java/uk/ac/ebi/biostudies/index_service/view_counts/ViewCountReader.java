package uk.ac.ebi.biostudies.index_service.view_counts;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Component responsible for locating and opening the view count file required by the application.
 *
 * <p>This class uses injected application properties to build the absolute or relative path to the
 * view count file by combining the base directory, update directory, and the submission statistics
 * file name.
 *
 * <p>The main responsibility is to provide a {@link BufferedReader} to read the contents of the
 * view count file, delegating file path construction and resource acquisition to this component.
 */
@Component
public class ViewCountReader {

  /** Directory where input files required by the application are located */
  @Value("${files.base-dir}")
  private String baseDir;

  /** Subdirectory or folder under base directory for update files */
  @Value("${files.update-dir}")
  private String updateDir;

  /** File name of the submission statistics CSV file */
  @Value("${files.submission-stats-file-name}")
  private String submissionStatsFileName;

  /**
   * Opens the view count file and returns a buffered reader to read its content.
   *
   * <p>The full path is constructed by combining {@code baseDir}, {@code updateDir}, and {@code
   * submissionStatsFileName} using platform-appropriate path separators.
   *
   * <p>The caller is responsible for closing the returned {@link BufferedReader} to release system
   * resources.
   *
   * @return a {@link BufferedReader} for the view count file content
   * @throws IOException if the file does not exist, is inaccessible, or an I/O error occurs during
   *     opening
   */
  public BufferedReader openViewCountFile() throws IOException {
    Path filePath = Paths.get(baseDir, updateDir, submissionStatsFileName);
    return Files.newBufferedReader(filePath);
  }
}
