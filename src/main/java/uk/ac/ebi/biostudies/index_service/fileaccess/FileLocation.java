package uk.ac.ebi.biostudies.index_service.fileaccess;

import com.amazonaws.services.s3.model.S3Object;
import java.nio.file.Path;

/**
 * Immutable description of where a requested file is physically stored.
 *
 * <p>A {@code FileLocation} represents the resolved backend location of a file
 * (either on NFS or in FIRE/S3) after path resolution has been performed.
 * It does not perform any I/O itself.
 *
 * @param storageMode Storage backend where the file resides (for example NFS or FIRE).
 * @param nfsPath Resolved filesystem path when the file is stored on NFS
 *                (typically non-null only when the storage mode is NFS).
 * @param firePath Logical object path when the file is stored in FIRE/S3
 *                 (typically non-null only when the storage mode is FIRE).
 *                 Usually corresponds to the key used to retrieve the object from the bucket.
 * @param s3Object Resolved {@link S3Object} instance when the file is stored in FIRE/S3
 *                 (typically non-null only when the storage mode is FIRE).
 *                 Callers are responsible for closing the underlying {@code S3Object}
 *                 once they finish consuming its content.
 */
public record FileLocation(
    StorageMode storageMode,
    Path nfsPath,
    String firePath,
    S3Object s3Object
) {

  /**
   * Returns {@code true} if this location represents a file stored on NFS.
   *
   * @return {@code true} when the storage mode is {@link StorageMode#NFS}, {@code false} otherwise
   */
  public boolean isNfs() {
    return storageMode == StorageMode.NFS;
  }

  /**
   * Returns {@code true} if this location represents a file stored in FIRE/S3.
   *
   * @return {@code true} when the storage mode is {@link StorageMode#FIRE}, {@code false} otherwise
   */
  public boolean isFire() {
    return storageMode == StorageMode.FIRE;
  }
}
