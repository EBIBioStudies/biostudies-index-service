package uk.ac.ebi.biostudies.index_service.search.files;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TermQuery;
import org.springframework.stereotype.Service;
import uk.ac.ebi.biostudies.index_service.exceptions.SubmissionNotAccessibleException;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.model.FileDocumentField;
import uk.ac.ebi.biostudies.index_service.model.IndexedSubmission;
import uk.ac.ebi.biostudies.index_service.search.engine.LuceneQueryExecutor;
import uk.ac.ebi.biostudies.index_service.search.engine.PaginatedResult;
import uk.ac.ebi.biostudies.index_service.search.engine.SearchCriteria;
import uk.ac.ebi.biostudies.index_service.submission.SubmissionRetriever;

/**
 * Service for searching file metadata within submissions using Lucene indexes.
 *
 * <p>Replicates RIBS {@code FilePaginationServiceImpl#getFileList} logic using modern {@link
 * LuceneQueryExecutor} infrastructure. Supports DataTables pagination (0-based offset), global
 * search, column filters, and sorting while fetching total file counts from submission metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSearchService {

  private final SubmissionRetriever retriever;
  private final LuceneQueryExecutor queryExecutor;

  /** Escapes Lucene query special characters (RIBS {@code StudyUtils.escape}). */
  public static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      if ("\\+ -!()^[]{}~|&/".indexOf(c) != -1 || c == '"' || c == ':' || c == '?') {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb.toString();
  }

  /** RIBS hasUnescapedDoubleQuote - detects phrase search */
  private static boolean hasUnescapedDoubleQuote(String search) {
    return search.contains("\"") && !search.contains("\\\"");
  }

  /**
   * Searches files for the given accession using DataTables-compatible parameters.
   *
   * @param accession submission accession (maps to {@code owner} field)
   * @param start 0-based offset (DataTables)
   * @param pageSize page size (-1 for all)
   * @param search global full-text search term
   * @param metadata include dynamic column metadata
   * @param columnSpecs column filters/sorts (from parsed DataTables JSON)
   * @param secretKey submission access key (for private studies)
   * @return search results with total/filtered counts and paginated files
   * @throws SubmissionNotAccessibleException if submission requires secretKey
   */
  public FileSearchResult searchFiles(
      String accession,
      int start,
      int pageSize,
      String search,
      boolean metadata,
      List<ColumnSpec> columnSpecs,
      String secretKey)
      throws SubmissionNotAccessibleException {

    try {
      // Get submission metadata (RIBS: getStudyInfo)
      var submissionOpt = retriever.getSubmissionByAccession(accession, secretKey);
      if (submissionOpt.isEmpty()) {
        return FileSearchResult.empty();
      }

      IndexedSubmission submission = submissionOpt.get();
      long totalFiles = submission.getNumberOfFiles();
      List<String> searchableColumns = submission.getFileAttributesNames();

      // Build query/sort (RIBS logic)
      Query baseQuery = new TermQuery(new Term(FileDocumentField.OWNER.getName(), accession));
      Query finalQuery = buildFileQuery(baseQuery, search, searchableColumns, columnSpecs);
      Sort sort = buildFileSort(columnSpecs);

      // DataTables → SearchCriteria pagination conversion
      int effectivePageSize = (pageSize == -1) ? Integer.MAX_VALUE : pageSize;
      int page = Math.max(1, (start / effectivePageSize) + 1);

      SearchCriteria criteria =
          new SearchCriteria.Builder(finalQuery).page(page, effectivePageSize).sort(sort).build();

      // Execute (your modern infra)
      PaginatedResult<Document> rawResults = queryExecutor.execute(IndexName.FILES, criteria);

      List<FileMetadata> files =
          rawResults.results().stream().map(doc -> documentToFileMetadata(doc, metadata)).toList();

      log.debug(
          "File search {}: {} total, {} filtered, {} returned",
          accession,
          totalFiles,
          rawResults.totalHits(),
          files.size());

      return new FileSearchResult(totalFiles, rawResults.totalHits(), files);

    } catch (SubmissionNotAccessibleException e) {
      throw e;
    } catch (Exception e) {
      log.error("File search failed for {}: {}", accession, e.getMessage(), e);
      return FileSearchResult.empty();
    }
  }

  private Query buildFileQuery(
      Query baseQuery, String search, List<String> columns, List<ColumnSpec> columnSpecs) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(baseQuery, BooleanClause.Occur.MUST);

    if (search != null && !search.trim().isEmpty() && !"**".equals(search.trim())) {
      if (hasUnescapedDoubleQuote(search)) {
        builder.add(phraseSearch(search), BooleanClause.Occur.MUST);
      } else {
        // KeywordAnalyzer: pass raw search (no modifySearchText splitting)
        Query searchQuery = applySearch(search.trim(), columns);
        builder.add(searchQuery, BooleanClause.Occur.MUST);
      }
    }

    // Per-field MUST filters
    for (ColumnSpec spec : columnSpecs) {
      if (spec.value() != null && !spec.value().trim().isEmpty()) {
        builder.add(
            new TermQuery(new Term(spec.name(), escape(spec.value().trim()))),
            BooleanClause.Occur.MUST);
      }
    }

    return builder.build();
  }

  private Sort buildFileSort(List<ColumnSpec> columnSpecs) {
    List<SortField> fields = new ArrayList<>();
    for (ColumnSpec spec : columnSpecs) {
      if (spec.dir() != null && spec.name() != null && !"x".equalsIgnoreCase(spec.name())) {
        boolean reverse = "desc".equalsIgnoreCase(spec.dir());
        if ("size".equalsIgnoreCase(spec.name())) {
          fields.add(new SortedNumericSortField(spec.name(), SortField.Type.LONG, reverse));
        } else {
          fields.add(new SortField(spec.name(), SortField.Type.STRING, reverse));
        }
      }
    }
    // RIBS default
    if (fields.isEmpty()) {
      fields.add(new SortField(FileDocumentField.POSITION.getName(), SortField.Type.LONG, false));
    }
    return new Sort(fields.toArray(new SortField[0]));
  }

  /** RIBS phraseSearch - keyword search on fileName */
  private Query phraseSearch(String search) {
    KeywordAnalyzer keywordAnalyzer = new KeywordAnalyzer();
    QueryParser parser = new QueryParser(FileDocumentField.NAME.getName(), keywordAnalyzer);
    parser.setAllowLeadingWildcard(true);
    // No setLowercaseExpandedTerms needed
    try {
      return parser.parse(escape(search));
    } catch (ParseException e) {
      return new TermQuery(new Term(FileDocumentField.NAME.getName(), "*"));
    }
  }

  /** RIBS applySearch - multi-field wildcard search */
  private Query applySearch(String search, List<String> columns) {
    KeywordAnalyzer keywordAnalyzer = new KeywordAnalyzer();
    MultiFieldQueryParser parser =
        new MultiFieldQueryParser(columns.toArray(String[]::new), keywordAnalyzer);
    parser.setAllowLeadingWildcard(true);

    try {
      // RIBS: "*term*" wrapping + escape
      String modifiedSearch = "*" + escape(search.trim()) + "*";
      return parser.parse(modifiedSearch);
    } catch (ParseException e) {
      log.warn("Multi-field search failed: {}", search, e);
      return new TermQuery(new Term(FileDocumentField.OWNER.getName(), "*"));
    }
  }

  /** Maps Lucene Document → FileMetadata. Includes dynamic fields only if metadata=true. */
  private FileMetadata documentToFileMetadata(Document doc, boolean metadata) {
    String path = doc.get(FileDocumentField.PATH.getName());
    String isDir = doc.get(FileDocumentField.IS_DIRECTORY.getName());
    String type = "true".equalsIgnoreCase(isDir) ? "directory" : "file";
    String sizeStr = doc.get(FileDocumentField.SIZE.getName());
    long size = (sizeStr != null) ? Long.parseLong(sizeStr) : 0L;

    Map<String, String> fileMetadata = new HashMap<>();
    if (metadata) {
      for (IndexableField field : doc.getFields()) {
        String name = field.name();
        if (!FileDocumentField.PATH.getName().equals(name)
            && !FileDocumentField.IS_DIRECTORY.getName().equals(name)
            && !FileDocumentField.SIZE.getName().equals(name)) {
          String value = field.stringValue();
          if (value != null) {
            fileMetadata.put(name, value);
          }
        }
      }
    }

    return new FileMetadata(path, type, size, fileMetadata);
  }
}
