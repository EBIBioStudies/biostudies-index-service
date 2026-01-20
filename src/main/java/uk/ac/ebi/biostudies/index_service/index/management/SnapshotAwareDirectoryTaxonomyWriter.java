package uk.ac.ebi.biostudies.index_service.index.management;

import java.io.IOException;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;

/**
 * {@link DirectoryTaxonomyWriter} variant that configures a {@link SnapshotDeletionPolicy} for the
 * underlying taxonomy index so callers can create safe point-in-time snapshots (e.g. for backup or
 * replication).
 */
public class SnapshotAwareDirectoryTaxonomyWriter extends DirectoryTaxonomyWriter {

  /**
   * Deletion policy used for the taxonomy index, wrapped in a {@link SnapshotDeletionPolicy} so
   * that recent commits can be snapshotted and protected from deletion.
   */
  private SnapshotDeletionPolicy facetIndexSnapShot;

  /**
   * Creates a new snapshot-aware taxonomy writer for the given directory.
   *
   * @param facetDirectory the Lucene {@link Directory} that stores the taxonomy index
   * @param openMode the {@link IndexWriterConfig.OpenMode} to use when opening the index
   * @throws IOException if the underlying index cannot be opened or created
   */
  public SnapshotAwareDirectoryTaxonomyWriter(
      Directory facetDirectory, IndexWriterConfig.OpenMode openMode) throws IOException {
    super(facetDirectory, openMode);
  }

  /**
   * Creates the {@link IndexWriterConfig} for this taxonomy writer and installs a {@link
   * SnapshotDeletionPolicy} that wraps {@link KeepOnlyLastCommitDeletionPolicy}.
   *
   * <p>This allows external code to take and release snapshots of the latest commit while still
   * keeping only the most recent non-snapshotted commit on disk.
   *
   * @param openMode the open mode requested for the underlying index writer
   * @return the configured {@link IndexWriterConfig} instance
   */
  @Override
  protected IndexWriterConfig createIndexWriterConfig(IndexWriterConfig.OpenMode openMode) {
    facetIndexSnapShot = new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
    return super.createIndexWriterConfig(openMode).setIndexDeletionPolicy(facetIndexSnapShot);
  }

  /**
   * Returns the snapshot-aware deletion policy configured for this taxonomy writer.
   *
   * <p>Callers can use this policy to take and release snapshots of the taxonomy index for backup
   * or replication purposes.
   *
   * @return the {@link SnapshotDeletionPolicy} currently in use
   */
  public SnapshotDeletionPolicy getDeletionPolicy() {
    return facetIndexSnapShot;
  }

  /**
   * Indicates whether this taxonomy writer is still open.
   *
   * <p>Internally calls {@link #ensureOpen()} and converts an {@link AlreadyClosedException} into a
   * simple boolean result.
   *
   * @return {@code true} if the writer is open; {@code false} if it has been closed
   */
  public boolean isOpen() {
    try {
      super.ensureOpen();
      return true;
    } catch (AlreadyClosedException exception) {
      return false;
    }
  }
}
