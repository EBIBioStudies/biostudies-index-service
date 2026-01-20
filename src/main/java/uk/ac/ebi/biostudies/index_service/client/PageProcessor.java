package uk.ac.ebi.biostudies.index_service.client;

import java.io.IOException;

/** Page processor for streaming (memory efficient). */
@FunctionalInterface
public interface PageProcessor {
  void process(PaginatedExtSubmissions page) throws IOException;
}
