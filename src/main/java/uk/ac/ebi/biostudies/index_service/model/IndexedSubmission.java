package uk.ac.ebi.biostudies.index_service.model;

import java.util.List;
import lombok.Data;

/**
 * Represents a submission entry stored in the search index.
 *
 * <p>This class holds a subset of submission metadata that is required for search, filtering and
 * display in the index service.
 */
@Data
public class IndexedSubmission {

  /** Internal identifier of the indexed document (e.g. Lucene or storage id). */
  private String id;

  /** Public accession of the submission (e.g. S-BSST1432). */
  private String accession;

  /** Access level of the submission (e.g. public, private). */
  private String access;

  /** Submission type (e.g. Study, Project, Collection). */
  private String type;

  /** Main author or first author of the submission. */
  private String author;

  /** Human‑readable title of the submission. */
  private String title;

  /** Number of times this submission has been viewed. */
  private Integer views;

  /** Relative path to the submission root within the storage system. */
  private String relPath;

  /** Indicates whether the submission has been released to users. */
  private boolean released;

  /** Storage backend or mode used for this submission (e.g. NFS, FIRE, S3). */
  private String storageMode;

  private long numberOfFiles;
  private long numberOfLinks;

  private List<String> sectionsWithFiles;
  private List<String> fileAttributesNames;
  private boolean hasFileIndexingError;
  private long releaseTime;
  private long modificationTime;
}
