package uk.ac.ebi.biostudies.index_service.fileaccess;

import com.amazonaws.services.s3.model.S3Object;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;
import uk.ac.ebi.biostudies.index_service.util.HttpTools;

/**
 * Provides access to the content and metadata of a resolved file location.
 *
 * <p>{@code FileContent} wraps a {@link FileLocation} and exposes operations to obtain an {@link
 * InputStream}, file length, and last-modified timestamp for the underlying resource. It mirrors
 * the behaviour that previously lived in {@code FileMetaData#getInputStream()}, {@code
 * getFileLength()} and {@code getLastModified()}.
 *
 * <p>Instances are reusable but not thread-safe. Callers are responsible for closing this instance
 * (which closes any underlying {@link InputStream} and {@link S3Object}) when they are finished
 * streaming the content.
 */
@Slf4j
public final class FileContent implements AutoCloseable {

  private final FileLocation location;

  /** Base FTP/HTTP URL used to access NFS files (formerly BASE_FTP_NFS_URL). */
  private final String nfsBaseFtpUrl;

  private final SecurityConfig securityConfig;

  /** Lazily opened stream for the current location, if any. */
  private InputStream inputStream;

  /**
   * Creates a new {@code FileContent} for the given location.
   *
   * @param location resolved backend location of the file
   * @param nfsBaseFtpUrl base FTP/HTTP URL used to access NFS files
   * @param securityConfig proxy / security configuration used for HTTP access
   */
  public FileContent(FileLocation location, String nfsBaseFtpUrl, SecurityConfig securityConfig) {
    this.location = location;
    this.nfsBaseFtpUrl = nfsBaseFtpUrl;
    this.securityConfig = securityConfig;
  }

  /**
   * Returns a streaming {@link InputStream} for the underlying file.
   *
   * <p>Behaviour is equivalent to the old {@code FileMetaData#getInputStream()}:
   *
   * <ul>
   *   <li>If an {@link InputStream} was already opened, it is returned.
   *   <li>If the file is in FIRE/S3 and an {@link S3Object} is present, {@code
   *       s3Object.getObjectContent()} is used.
   *   <li>If the file is on NFS and a path is present, a full URL is constructed from {@code
   *       nfsBaseFtpUrl} and the path, and {@link HttpTools#fetchLargeFileStream(String,
   *       SecurityConfig)} is used.
   * </ul>
   *
   * @return an open {@link InputStream}, or {@code null} if the stream could not be created
   */
  public synchronized InputStream openStream() {
    if (inputStream != null) {
      return inputStream;
    }
    try {
      if (location.isFire() && location.s3Object() != null) {
        inputStream = location.s3Object().getObjectContent();
      } else if (location.isNfs() && location.nfsPath() != null) {
        String url = buildNfsUrl(location.nfsPath());
        inputStream = HttpTools.fetchLargeFileStream(url, securityConfig);
      }
    } catch (Exception exception) {
      log.error("Problem creating input stream for file at location {}", location, exception);
    }
    return inputStream;
  }

  /**
   * Returns the last-modified timestamp of the underlying resource in milliseconds since the epoch.
   *
   * @return last-modified time in milliseconds, or {@code 0L} if it cannot be determined
   * @throws IOException if an error occurs while querying the filesystem
   */
  public long getLastModified() throws IOException {
    if (location.isFire() && location.s3Object() != null) {
      return location.s3Object().getObjectMetadata().getLastModified().getTime();
    }
    if (location.isNfs() && location.nfsPath() != null) {
      return Files.getLastModifiedTime(location.nfsPath()).toMillis();
    }
    return 0L;
  }

  /**
   * Returns the length of the underlying file in bytes.
   *
   * @return file length in bytes, or {@code 0L} if it cannot be determined
   * @throws IOException if an error occurs while querying the filesystem
   */
  public long getFileLength() throws IOException {
    if (location.isFire() && location.s3Object() != null) {
      return location.s3Object().getObjectMetadata().getContentLength();
    }
    if (location.isNfs() && location.nfsPath() != null) {
      return Files.size(location.nfsPath());
    }
    return 0L;
  }

  /** Closes any open {@link InputStream} and the underlying {@link S3Object}, if present. */
  @Override
  public synchronized void close() {
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (Exception e) {
      log.error("Problem closing input stream for {}", location, e);
    } finally {
      inputStream = null;
    }
    try {
      S3Object s3 = location.s3Object();
      if (s3 != null) {
        s3.close();
      }
    } catch (Exception e) {
      log.error("Problem closing S3Object for {}", location, e);
    }
  }

  private String buildNfsUrl(Path path) {
    String pathStr = path.toString().replace('\\', '/');
    if (nfsBaseFtpUrl == null || nfsBaseFtpUrl.isEmpty()) {
      return pathStr;
    }
    return nfsBaseFtpUrl.endsWith("/") || pathStr.startsWith("/")
        ? nfsBaseFtpUrl + pathStr
        : nfsBaseFtpUrl + "/" + pathStr;
  }
}
