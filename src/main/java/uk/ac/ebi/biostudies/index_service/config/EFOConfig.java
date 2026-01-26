package uk.ac.ebi.biostudies.index_service.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for EFO (Experimental Factor Ontology) loading and indexing. Properties loaded from
 * {@code application.properties} with prefix {@code efo}.
 *
 * <p>Initializes stop words set after Spring property injection via {@link InitializingBean}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "efo")
public class EFOConfig implements InitializingBean {

  /**
   * Parsed stop words set (lowercase, trimmed). Populated after properties set via {@link
   * #afterPropertiesSet()}.
   */
  private final Set<String> stopWordsSet = new HashSet<>();

  private String stopwords;
  private String synonyms;
  private String ignoreList;
  private String owlFilename;
  private String updateUrl;
  private String localOwlFilename;

  /**
   * Parses comma-separated stop words into set. Called automatically by Spring after property
   * injection.
   */
  @Override
  public void afterPropertiesSet() {
    if (stopwords != null && !stopwords.isBlank()) {
      String[] words = stopwords.split("\\s*,\\s*");
      for (String word : words) {
        if (!word.isBlank()) {
          stopWordsSet.add(word.toLowerCase().trim());
        }
      }
    }
  }

  /**
   * Returns unmodifiable view of stop words set. Prevents external modification of internal set.
   */
  public Set<String> getStopWordsSet() {
    return Collections.unmodifiableSet(stopWordsSet);
  }
}
