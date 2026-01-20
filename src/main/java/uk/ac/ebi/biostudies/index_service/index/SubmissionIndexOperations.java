package uk.ac.ebi.biostudies.index_service.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

/** Abstraction for submission indexing operations on Lucene writers and refresh logic. */
public interface SubmissionIndexOperations {

  /**
   * Returns the writer for the files index.
   *
   * @return files index writer
   */
  IndexWriter getFilesIndexWriter();

  /**
   * Returns the writer for the page tab index.
   *
   * @return page tab index writer
   */
  IndexWriter getPageTabIndexWriter();

  /**
   * Returns the writer for the main submission index.
   *
   * @return submission index writer
   */
  IndexWriter getSubmissionIndexWriter();

  /**
   * Deletes documents matching the given term from the page tab index.
   *
   * @param term term to match (e.g., accession)
   * @throws IOException if delete fails
   */
  void deletePageTabDocuments(Term term) throws IOException;

  /**
   * Updates the submission document in the main index.
   *
   * @param idTerm ID term for the document
   * @param document new/updated document
   * @throws IOException if update fails
   */
  void updateSubmissionDocument(Term idTerm, Document document) throws IOException;

  /**
   * Sets live commit user data on the submission index writer (e.g., updateTime).
   *
   * @param commitData entries to set
   */
  void setSubmissionCommitData(Iterable<? extends java.util.Map.Entry<String, String>> commitData);

  /** Commits submission-related indices (submissions, files, page tabs). */
  void commitSubmissionRelatedIndices();

  /** Refreshes the taxonomy reader. */
  void refreshTaxonomyReader();

  /** Refreshes all managed indices. */
  void refreshAll();

  /**
   * Deletes documents matching the query from submission index.
   *
   * @param query deletion query
   * @throws IOException if delete fails
   */
  void deleteSubmissionDocuments(Query query) throws IOException;

  /** Commits the submission and files writers explicitly. */
  void commitSubmissionAndFiles();
}
