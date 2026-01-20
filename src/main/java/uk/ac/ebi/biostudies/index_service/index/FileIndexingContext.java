package uk.ac.ebi.biostudies.index_service.index;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.Getter;
import org.apache.lucene.index.IndexWriter;

/**
 * Thread-safe context for BioStudies file indexing operations.
 *
 * <p>Encapsulates mutable state shared across parallel file processing pipelines. All collections
 * are pre-configured for thread-safety (synchronized lists, concurrent sets). Used to track:
 *
 * <ul>
 *   <li>Lucene indexing ({@link #indexWriter})
 *   <li>File processing statistics ({@link #fileCounter})
 *   <li>File attribute columns ({@link #fileColumns})
 *   <li>File-containing sections ({@link #sectionsWithFiles})
 *   <li>Searchable file metadata ({@link #searchableFileMetadata})
 *   <li>Processing errors ({@link #hasIndexingError})
 * </ul>
 */
@Builder
@Getter
public class FileIndexingContext {

  /** Lucene IndexWriter for creating file documents. */
  private final IndexWriter indexWriter;

  /** Counts total files processed across all file lists. */
  private final AtomicLong fileCounter;

  /** File attribute column names discovered during processing. */
  private final Set<String> fileColumns;

  /** Unique sections containing file lists (for facet generation). */
  private final Set<String> sectionsWithFiles;

  /** Concatenated file metadata for full-text search (name:value pairs). */
  private final Set<String> searchableFileMetadata;

  /** Tracks if any file processing errors occurred. */
  private final AtomicBoolean hasIndexingError;

  /** Submission document valueMap populated with file indexing results. */
  private final Map<String, Object> valueMap;
}
