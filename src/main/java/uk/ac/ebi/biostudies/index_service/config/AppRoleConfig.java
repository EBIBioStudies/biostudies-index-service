package uk.ac.ebi.biostudies.index_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppRoleConfig {
  /** Writer role: when the app is configured to listen to the queue and to modify Lucene indexes */
  public static final String WRITER_ROLE = "writer";

  @Value("${indexer.role}")
  private String indexerRole;

  public boolean isWriterRole() {
    return WRITER_ROLE.equals(indexerRole);
  }
}
