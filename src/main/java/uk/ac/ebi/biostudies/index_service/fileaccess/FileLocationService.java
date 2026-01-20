package uk.ac.ebi.biostudies.index_service.fileaccess;

import com.amazonaws.services.s3.model.S3Object;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.config.FilesConfig;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;
import uk.ac.ebi.biostudies.index_service.storage.fire.FireService;
import uk.ac.ebi.biostudies.index_service.util.SubmissionUtils;

/**
 * Resolves a {@link FileRequest} into a {@link FileContent} that can be streamed to callers.
 *
 * <p>This service encapsulates all storage-specific logic for:
 *
 * <ul>
 *   <li>NFS JSON cache fast-path for pagetab/filelist files.
 *   <li>NFS-based storage resolution.
 *   <li>FIRE/S3-based storage resolution, including fallback strategies.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileLocationService {

  private final FireService fireService;
  private final FilesConfig filesConfig;
  private final SecurityConfig securityConfig;

  /**
   * Resolves the given {@link FileRequest} into a {@link FileContent}.
   *
   * <p>Behaviour:
   *
   * <ul>
   *   <li>Try NFS JSON cache if enabled and the request is for a JSON file.
   *   <li>Otherwise, resolve using the configured {@link StorageMode}.
   * </ul>
   *
   * @param request file request coming from the client/UI
   * @return a {@link FileContent} wrapping the resolved location
   * @throws FileNotFoundException if no suitable file can be found
   */
  public FileContent resolveContent(FileRequest request) throws FileNotFoundException {
    // 1. NFS JSON cache fast-path (pagetab or filelist JSON)
    FileContent cachedJson = tryResolveFromNfsJsonCache(request);
    if (cachedJson != null) {
      return cachedJson;
    }

    // 2. Resolve according to storage mode
    StorageMode storageMode = request.storageMode();
    if (storageMode == StorageMode.FIRE) {
      FileLocation location = resolveFireLocation(request);
      return new FileContent(location, filesConfig.getFtpNfsUrl(), securityConfig);
    }
    if (storageMode == StorageMode.NFS) {
      FileLocation location = resolveNfsLocation(request);
      return new FileContent(location, filesConfig.getFtpNfsUrl(), securityConfig);
    }

    log.error("No valid storage mode for file: {} {}", storageMode, request.requestedPath());
    throw new FileNotFoundException(request.requestedPath());
  }

  private FileContent tryResolveFromNfsJsonCache(FileRequest request) {
    String requestedPath = request.requestedPath();
    if (!filesConfig.isPageTabHasNFSBackup()
        || requestedPath == null
        || !requestedPath.endsWith(".json")) {
      return null;
    }

    Path cachePath =
        Paths.get(
            filesConfig.getNfsCachePath(), request.studyRelativePath(), "Files", requestedPath);

    if (!Files.exists(cachePath)) {
      return null;
    }

    try {
      FileLocation location = new FileLocation(StorageMode.NFS, cachePath, null, null);
      FileContent content =
          new FileContent(location, filesConfig.getHttpFtpNfsUrl(), securityConfig);
      // Open the stream from the filesystem directly for the cache case.
      content.openStream(); // will use NFS/HTTP according to FileContent
      return content;
    } catch (Exception e) {
      log.debug("Problem creating input stream from NFS cache at {}", cachePath, e);
      return null;
    }
  }

  private FileLocation resolveNfsLocation(FileRequest request) throws FileNotFoundException {
    String relativePath = request.studyRelativePath();

    // Private studies: adjust relative path as before
    if (request.secretKey() != null) {
      relativePath =
          SubmissionUtils.modifyRelativePathForPrivateStudies(request.secretKey(), relativePath);
    }

    String requestedPath = request.requestedPath();
    String base =
        relativePath
            + (request.thumbnailRequested()
                ? "/Thumbnails/"
                : (requestedPath.startsWith("Files/") ? "/" : "/Files/"));
    String suffix = request.thumbnailRequested() ? ".thumbnail.png" : "";
    Path downloadFile = Paths.get(base + requestedPath + suffix);

    // Original code used HttpTools.isValidUrl(downloadFile) against FTP. Here we just check
    // existence.
    if (!Files.exists(downloadFile)) {
      log.error("Could not find NFS file {}", downloadFile);
      throw new FileNotFoundException(downloadFile.toString());
    }

    return new FileLocation(StorageMode.NFS, downloadFile, null, null);
  }

  private FileLocation resolveFireLocation(FileRequest request) throws FileNotFoundException {
    S3Object s3Object = null;
    String path =
        request.studyRelativePath()
            + (request.thumbnailRequested()
                ? "/Thumbnails/"
                : (request.requestedPath().startsWith("Files/") ? "/" : "/Files/"))
            + request.requestedPath()
            + (request.thumbnailRequested() ? ".thumbnail.png" : "");

    // Special case for main accession files (json/xml/tsv)
    if (isMainAccessionFile(request)) {
      path = request.studyRelativePath() + "/" + request.requestedPath();
    }

    try {
      s3Object = fireService.getFireObjectByPath(path);
    } catch (Exception ex1) {
      try {
        if (isAccessionTsv(request)) {
          log.debug("{} not found. Trying old .pagetab.tsv file.", path);
          path = request.studyRelativePath() + "/" + request.accession() + ".pagetab.tsv";
        } else {
          log.debug("{} not found and might be a folder. Trying zipped archive.", path);
          path = path + ".zip";
        }
        s3Object = fireService.getFireObjectByPath(path);
      } catch (Exception ex4) {
        try {
          if (s3Object != null) {
            s3Object.close();
          }
        } catch (Exception closeEx) {
          log.debug("Cannot close FIRE HTTP connection", closeEx);
        }
        throw new FileNotFoundException(ex4.getMessage());
      }
    }

    return new FileLocation(StorageMode.FIRE, null, path, s3Object);
  }

  private boolean isMainAccessionFile(FileRequest request) {
    String acc = request.accession();
    String req = request.requestedPath();
    return req.equals(acc + ".json") || req.equals(acc + ".xml") || req.equals(acc + ".tsv");
  }

  private boolean isAccessionTsv(FileRequest request) {
    String acc = request.accession();
    String req = request.requestedPath();
    return req.equals(acc + ".tsv");
  }
}
