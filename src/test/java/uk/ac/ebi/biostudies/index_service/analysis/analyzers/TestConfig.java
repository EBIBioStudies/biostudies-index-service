package uk.ac.ebi.biostudies.index_service.analysis.analyzers;

import org.apache.lucene.analysis.CharArraySet;
import uk.ac.ebi.biostudies.index_service.index.LuceneIndexConfig;

class TestConfig extends LuceneIndexConfig {
  private final CharArraySet stopWords;

  TestConfig(CharArraySet stopWords) {
    this.stopWords = stopWords;
  }

  @Override
  public CharArraySet getStopWordsCache() {
    return stopWords;
  }
}
