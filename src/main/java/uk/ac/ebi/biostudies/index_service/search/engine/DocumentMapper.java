package uk.ac.ebi.biostudies.index_service.search.engine;

import org.apache.lucene.document.Document;

/**
 * Maps Lucene {@link Document} instances to domain-specific DTOs.
 *
 * <p>Implementations are responsible for extracting fields from raw Lucene documents and
 * transforming them into typed domain objects, handling type conversions and default values as
 * needed.
 *
 * @param <T> the type of DTO produced by this mapper
 */
public interface DocumentMapper<T> {

  /**
   * Converts a Lucene document to a domain-specific DTO.
   *
   * @param document the Lucene document to map
   * @return the mapped DTO instance
   * @throws NullPointerException if document is null
   * @throws IllegalStateException if required fields are missing or invalid
   */
  T toDto(Document document);
}
