package uk.ac.ebi.biostudies.index_service.search.engine;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.search.SearchHit;

/**
 * Executes Lucene queries with pagination and sorting support across managed indexes. Handles
 * searcher lifecycle via IndexManager's acquire/release pattern.
 */
@Slf4j
@Service
public class LuceneQueryExecutor {

  private static final int MAX_PAGE_SIZE = 1000;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final IndexManager indexManager;

  public LuceneQueryExecutor(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  public PaginatedResult execute(
      IndexName indexName, Query query, int page, int pageSize, Sort sort) throws IOException {

    IndexSearcher searcher = indexManager.acquireSearcher(indexName);
    try {
      // Limit page size to prevent memory issues
      int limitedPageSize = Math.min(pageSize, MAX_PAGE_SIZE);

      // Calculate total documents needed (all pages up to current page)
      int totalDocsNeeded = page * limitedPageSize;

      // Execute search
      TopDocs topDocs =
          sort != null
              ? searcher.search(query, totalDocsNeeded, sort)
              : searcher.search(query, totalDocsNeeded);

      // Calculate pagination boundaries for current page only
      int start = (page - 1) * limitedPageSize;
      int end = Math.min(start + limitedPageSize, topDocs.scoreDocs.length);

      // Fetch documents for current page only
      List<Document> docs = new ArrayList<>();
      StoredFields storedFields = searcher.storedFields();
      for (int i = start; i < end; i++) {
        Document doc = storedFields.document(topDocs.scoreDocs[i].doc);
        docs.add(doc);
      }

      boolean isTotalHitsExact = topDocs.totalHits.relation() == TotalHits.Relation.EQUAL_TO;

      return new PaginatedResult(
          toSearchHits(docs), page, limitedPageSize, topDocs.totalHits.value(), isTotalHitsExact);

    } finally {
      indexManager.releaseSearcher(indexName, searcher);
    }
  }

  /** Executes a query without sorting (relevance-based). */
  public PaginatedResult execute(IndexName indexName, Query query, int page, int pageSize)
      throws IOException {
    return execute(indexName, query, page, pageSize, null);
  }

  private List<SearchHit> toSearchHits(List<Document> docs) {
    return docs.stream().map(this::toSearchHit).collect(Collectors.toList());
  }

  private SearchHit toSearchHit(Document doc) {
    String releaseDateStr = doc.get("release_date");
    if (releaseDateStr == null) {
      throw new IllegalStateException("Missing release_date for document: " + doc.get("accession"));
    }

    LocalDate releaseDate = LocalDate.parse(releaseDateStr, DATE_FORMATTER);

    return new SearchHit(
        doc.get("accession"),
        doc.get("type"),
        doc.get("title"),
        doc.get("author"),
        parseIntSafe(doc.get("links")),
        parseIntSafe(doc.get("files")),
        releaseDate,
        parseIntSafe(doc.get("views")),
        Boolean.parseBoolean(doc.get("isPublic")),
        doc.get("content"));
  }

  private int parseIntSafe(String value) {
    return value != null ? Integer.parseInt(value) : 0;
  }
}
