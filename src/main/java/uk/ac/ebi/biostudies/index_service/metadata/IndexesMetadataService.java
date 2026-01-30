package uk.ac.ebi.biostudies.index_service.metadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;
import uk.ac.ebi.biostudies.index_service.index.management.IndexContainer;

/**
 * Service for retrieving metadata about all managed Lucene indexes.
 *
 * <p>This service collects information such as index location, size, document count, and last
 * update time from index commits for each {@link IndexName}.
 *
 * <p>Errors during metadata retrieval for individual indexes are logged but do not affect the
 * overall result.
 */
@Slf4j
@Service
public class IndexesMetadataService {

  private final LuceneIndexConfig luceneIndexConfig;
  private final IndexContainer indexContainer;

  /**
   * Constructs a new instance with the required dependencies.
   *
   * @param luceneIndexConfig configuration for Lucene index paths
   * @param indexContainer container managing index searchers
   */
  public IndexesMetadataService(
      LuceneIndexConfig luceneIndexConfig, IndexContainer indexContainer) {
    this.luceneIndexConfig = luceneIndexConfig;
    this.indexContainer = indexContainer;
  }

  /**
   * Retrieves metadata for all indexes.
   *
   * @return list of {@link IndexMetadataDto} for each index, potentially missing failed indexes
   */
  public List<IndexMetadataDto> getAllIndexesMetadata() {
    return findAllIndexesMetadata();
  }

  /**
   * Finds metadata for all available indexes by iterating over {@link IndexName} values.
   *
   * @return list of index metadata DTOs
   */
  private List<IndexMetadataDto> findAllIndexesMetadata() {
    List<IndexMetadataDto> metadata = new ArrayList<>();
    for (IndexName indexName : IndexName.values()) {
      try {
        IndexMetadataDto indexMetadataDto = getIndexMetadata(indexName);
        metadata.add(indexMetadataDto);
      } catch (Exception e) {
        log.error("Error reading index metadata for index {}: {}", indexName.getIndexName(), e.getMessage());
      }
    }
    return metadata;
  }

  /**
   * Retrieves detailed metadata for a specific index.
   *
   * <p>Acquires a searcher, reads document count and commit user data for update time, and
   * calculates folder size.
   *
   * @param indexName the index to query
   * @return populated {@link IndexMetadataDto}
   * @throws IOException if index access fails
   */
  private IndexMetadataDto getIndexMetadata(IndexName indexName) throws IOException {
    IndexMetadataDto indexMetadata = new IndexMetadataDto();
    indexMetadata.setName(indexName.getIndexName());
    indexMetadata.setLocation(luceneIndexConfig.getIndexPath(indexName));
    indexMetadata.setSize(getFolderSize(luceneIndexConfig.getIndexPath(indexName)));
    IndexSearcher searcher = indexContainer.acquireSearcher(indexName);
    try {
      IndexReader reader = searcher.getIndexReader();
      int numDocs = reader.numDocs();
      indexMetadata.setNumberOfDocuments(numDocs);
      // Last commit info
      DirectoryReader dirReader = null;
      if (reader instanceof DirectoryReader) {
        dirReader = (DirectoryReader) reader;
      }

      if (dirReader != null) {
        IndexCommit commit = dirReader.getIndexCommit();
        var commitUserData = commit.getUserData();
        if (commitUserData != null) {
          if (commitUserData.containsKey("updateTime")) {
            var updateTime = commitUserData.get("updateTime");
            long millis = Long.parseLong(updateTime);
            LocalDateTime ldt =
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
            indexMetadata.setUpdateTime(ldt);
          }
        }
      } else {
        log.error("Reader is not a DirectoryReader for index {}", indexName.getIndexName());
      }
      // Access other stats
    } catch (Exception e) {
      log.error("Error reading index metadata for index {}", indexName.getIndexName(), e);
    }
    finally {
      indexContainer.releaseSearcher(indexName, searcher);
    }
    return indexMetadata;
  }

  /**
   * Calculates the total size of an index directory in bytes.
   *
   * <p>Recursively sums sizes of all regular files, ignoring directories and errors.
   *
   * @param pathStr string path to the index directory
   * @return total size in bytes as double
   * @throws IOException if path access fails initially
   */
  private double getFolderSize(String pathStr) throws IOException {
    double size = 0;
    Path indexPath = Path.of(pathStr);
    if (Files.isDirectory(indexPath)) {
      try (Stream<Path> pathStream = Files.walk(indexPath)) {
        size =
            pathStream
                .filter(Files::isRegularFile)
                .mapToLong(
                    p -> {
                      try {
                        return Files.size(p);
                      } catch (IOException e) {
                        log.debug("Failed to get size for file: {}", p, e);
                        return 0;
                      }
                    })
                .sum();
      }
    }
    return size;
  }

}
