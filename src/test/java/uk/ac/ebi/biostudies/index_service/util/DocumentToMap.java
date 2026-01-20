package uk.ac.ebi.biostudies.index_service.util;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;

// Utility class to convert a Lucene document into a map to make comparisons easier
public class DocumentToMap {
  public static Map<String, Object> convert(Document document) {
    Map<String, Object> map = new HashMap<>();
    if (document != null) {
      document.getFields().forEach(field -> {
        String name = field.name();
        String value = field.stringValue();
        map.put(name, value);
      });
    }
    return map;
  }
}
