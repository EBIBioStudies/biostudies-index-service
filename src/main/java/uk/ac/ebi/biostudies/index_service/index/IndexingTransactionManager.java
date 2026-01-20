package uk.ac.ebi.biostudies.index_service.index;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.analysis.analyzers.AttributeFieldAnalyzer;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;

/**
 * Coordinates submission indexing transactions: document updates + commits/refreshes.
 */
@Slf4j
@Service
public class IndexingTransactionManager {

  private final IndexManager indexManager;
  private final AttributeFieldAnalyzer attributeFieldAnalyzer;

  public IndexingTransactionManager(IndexManager indexManager,
      AttributeFieldAnalyzer attributeFieldAnalyzer) {
    this.indexManager = indexManager;
    this.attributeFieldAnalyzer = attributeFieldAnalyzer;
  }

  public IndexWriter getFilesIndexWriter() {
    return indexManager.getFilesIndexWriter();
  }

  /**
   * Updates submission doc: deletes pageTab + updates main index.
   */
  public void updateSubmissionDocument(Document submissionDocument, String accNo) throws IOException {
    indexManager
        .getPageTabIndexWriter()
        .deleteDocuments(new Term(FieldName.ACCESSION.getName(), accNo.toLowerCase()));
    indexManager
        .getSubmissionIndexWriter()
        .updateDocument(new Term(FieldName.ID.getName(), accNo), submissionDocument);
  }

  public void commit() throws IOException {
    Map<String, String> commitData = new HashMap<>();
    commitData.put("updateTime", String.valueOf(Instant.now().toEpochMilli()));
    log.debug("commitData: {}", commitData);
    indexManager.getSubmissionIndexWriter().setLiveCommitData(commitData.entrySet());
    indexManager.commitSubmissionRelatedIndices();
    log.debug("Data commited");
    indexManager.refreshAll();
    log.debug("Indices refreshed");
    //TODO: searchService.clearStatsCache();
  }

  public void deleteSubmission(String accNo, FilesIndexer filesIndexer)
      throws IOException, ParseException {
    log.warn("Deleting submission {}", accNo);
    if (accNo == null || accNo.isEmpty()) {
      return;
    }

    QueryParser parser = new QueryParser(FieldName.ACCESSION.getName(), attributeFieldAnalyzer);
    String queryStr = FieldName.ACCESSION + ":" + accNo;
    parser.setSplitOnWhitespace(true);
    Query query = parser.parse(queryStr);
    indexManager.getSubmissionIndexWriter().deleteDocuments(query);
    filesIndexer.removeFileDocuments(indexManager.getFilesIndexWriter(), accNo);
    indexManager.getPageTabIndexWriter().deleteDocuments(query);
    commit();
  }

}
