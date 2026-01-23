package uk.ac.ebi.biostudies.index_service.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Security configuration loaded from classpath:properties.properties.
 *
 * <p>This is a facade that aggregates various efo-index configuration properties.
 */
@Getter
@Component
@PropertySource("classpath:efo.properties")
public class EFOConfig {

  private final String stopWords;
  private final String synonymFilename;
  private final String ignoreListFilename;
  private final String owlFilename;
  private final String updateUrl;

  public EFOConfig(
      @Value("${efo.stopwords:}") String stopWords,
      @Value("${efo.synonyms:}") String synonymFilename,
      @Value("${efo.ignore-list:}") String ignoreListFilename,
      @Value("${efo.owl-filename:}") String owlFilename,
      @Value("${efo.update-url:}") String updateUrl) {
    this.stopWords = stopWords;
    this.synonymFilename = synonymFilename;
    this.ignoreListFilename = ignoreListFilename;
    this.owlFilename = owlFilename;
    this.updateUrl = updateUrl;
  }
}
