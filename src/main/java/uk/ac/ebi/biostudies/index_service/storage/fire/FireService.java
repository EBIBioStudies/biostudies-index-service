package uk.ac.ebi.biostudies.index_service.storage.fire;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.config.FireConfig;

/**
 * Client service for accessing files stored in FIRE (S3-compatible object storage).
 *
 * <p>This service wraps {@link AmazonS3} operations and applies BioStudies-specific
 * configuration (bucket name, path conventions).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FireService {

  private final FireConfig fireConfig;

  @Qualifier("S3DownloadClient")
  private final AmazonS3 s3DownloadClient;

  /**
   * Loads a FIRE object fully into memory and returns a cloned {@link InputStream}.
   *
   * <p>This method is only suitable for small files (e.g. pagetabs, metadata). Do not use it
   * for large attached files.
   *
   * @param path object key within the FIRE bucket
   * @return an {@link InputStream} backed by an in-memory copy of the object content
   * @throws IOException if the object cannot be read
   */
  public InputStream cloneFireS3ObjectStream(String path) throws IOException {
    log.debug("Accessing FIRE S3 object at {}", path);
    S3Object s3Object = null;
    try {
      s3Object = getFireObjectByPath(path);
      byte[] bytes = s3Object.getObjectContent().readAllBytes();
      return new ByteArrayInputStream(bytes);
    } catch (Exception exception) {
      log.error("Error reading FIRE object at {}", path, exception);
      if (exception.getMessage() != null && exception.getMessage().contains("Not Found")) {
        throw new FileNotFoundException(exception.getMessage());
      }
      return null;
    } finally {
      if (s3Object != null) {
        try {
          s3Object.close();
        } catch (Exception closeEx) {
          log.debug("Problem closing FIRE S3Object for {}", path, closeEx);
        }
      }
    }
  }

  /**
   * Retrieves a live {@link S3Object} for the given FIRE path.
   *
   * <p>The caller is responsible for closing the returned {@code S3Object} (or the stream
   * it provides) when finished.
   *
   * @param path object key within the FIRE bucket
   * @return {@link S3Object} pointing to the requested object
   * @throws FileNotFoundException if the object cannot be retrieved
   */
  public S3Object getFireObjectByPath(String path) throws FileNotFoundException {
    String bucketName = fireConfig.getS3Bucket();
    GetObjectRequest request = new GetObjectRequest(bucketName, path);
    try {
      return s3DownloadClient.getObject(request);
    } catch (Exception e) {
      log.error("Error retrieving FIRE object {}/{}", bucketName, path, e);
      throw new FileNotFoundException("FIRE object not found at path: " + path);
    }
  }

  /**
   * Recursively collects all object keys under the given list of prefixes.
   *
   * @param pathNameList initial list of keys or prefixes
   * @return stack containing all discovered file keys
   * @throws Exception if listing fails
   */
  public Stack<String> getAllDirectoryContent(List<String> pathNameList) throws Exception {
    Stack<String> allFiles = new Stack<>();
    if (pathNameList == null || pathNameList.isEmpty()) {
      return allFiles;
    }

    // Initial files (paths that look like they contain a dot in the last segment)
    allFiles.addAll(
        pathNameList.stream()
            .filter(path -> StringUtils.substringAfterLast(path, "/").contains("."))
            .collect(Collectors.toList()));

    // Initial directories
    Stack<String> directories = new Stack<>();
    directories.addAll(
        pathNameList.stream()
            .filter(path -> !StringUtils.substringAfterLast(path, "/").contains("."))
            .collect(Collectors.toList()));

    try {
      while (!directories.isEmpty()) {
        String currentPrefix = directories.pop();
        ObjectListing objectListing =
            s3DownloadClient.listObjects(fireConfig.getS3Bucket(), currentPrefix);
        do {
          allFiles.addAll(
              objectListing.getObjectSummaries().stream()
                  .map(sum -> sum.getKey())
                  .collect(Collectors.toList()));
          List<String> embeddedDirectories = objectListing.getCommonPrefixes();
          directories.addAll(embeddedDirectories);
        } while (objectListing.isTruncated());
      }
    } catch (Exception e) {
      log.error("Error listing FIRE directory content", e);
    }

    return allFiles;
  }

  /**
   * Checks whether the given path represents a non-empty "folder" in FIRE.
   *
   * @param path prefix to check
   * @return {@code true} if there is at least one object under the given prefix
   */
  public boolean isValidFolder(String path) {
    try {
      ObjectListing objectListing =
          s3DownloadClient.listObjects(fireConfig.getS3Bucket(), path);
      return objectListing.getMaxKeys() > 0;
    } catch (Exception e) {
      log.debug("Error checking FIRE folder validity for {}", path, e);
      return false;
    }
  }
}
