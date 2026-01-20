package uk.ac.ebi.biostudies.index_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for file access locations and behaviour.
 *
 * <p>Values are loaded from {@code files.properties} (prefix {@code files.*}).
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "files")
public class FilesConfig {

  /**
   * FTP URL used when accessing FIRE-based files over FTP.
   */
  private String ftpFireUrl;

  /**
   * FTP URL used when accessing NFS-based files over FTP.
   */
  private String ftpNfsUrl;

  /**
   * HTTP URL prefix used when accessing NFS-based files via HTTP/FTP.
   */
  private String httpFtpNfsUrl;

  /**
   * HTTP URL prefix used when accessing FIRE-based files via HTTP/FTP.
   */
  private String httpFtpFireUrl;

  /**
   * Indicates whether private NFS directories have already been migrated.
   */
  private boolean isMigratedNfsPrivateDirectory;

  /**
   * Indicates whether the migration process is still in progress.
   */
  private boolean migratingNotCompleted;

  /**
   * Globus URL prefix for NFS-based files.
   */
  private String globusNfsUrl;

  /**
   * Globus URL prefix for FIRE-based files.
   */
  private String globusFireUrl;

  /**
   * Local directory used to store generated thumbnails.
   */
  private String thumbnailsDirectory;

  /**
   * Whether pagetab/filelist JSON files have an NFS backup cache.
   */
  private boolean pageTabHasNFSBackup;

  /**
   * Root path of the NFS cache used for pagetab/filelist JSON files.
   */
  private String nfsCachePath;
}
