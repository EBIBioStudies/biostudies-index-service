package uk.ac.ebi.biostudies.index_service.fileaccess;

/**
 * Represents the backend storage used for a file.
 */
public enum StorageMode {

  /**
   * File is stored on the NFS filesystem.
   */
  NFS,

  /**
   * File is stored in FIRE (S3-compatible object storage).
   */
  FIRE
}
