package uk.ac.ebi.biostudies.index_service.registry.service;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.analysis.AnalyzerName;
import uk.ac.ebi.biostudies.index_service.parsing.ParserName;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

@Component
public class DefaultCollectionRegistryValidator implements CollectionRegistryValidator {
  private static final Set<String> ALLOWED_FIELD_TYPES = FieldType.allowedFieldTypes();

  private static final Set<String> ALLOWED_ANALYZERS = AnalyzerName.allowedAnalyzers();

  private static final Set<String> ALLOWED_PARSERS = ParserName.allowedParsers();

  @Override
  public void validate(CollectionRegistry registry) {
    if (registry == null) {
      throw new IllegalStateException("CollectionRegistry must not be null");
    }

    List<CollectionDescriptor> collections = registry.getCollections();
    for (CollectionDescriptor collection : collections) {
      for (PropertyDescriptor property : collection.getProperties()) {
        validateFieldType(property);
        validateAnalyzer(property);
        validateParser(property);
        validateJsonPath(property);
      }
    }
  }

  private void validateFieldType(PropertyDescriptor property) {
    FieldType fieldType = property.getFieldType();
    if (fieldType == null || !ALLOWED_FIELD_TYPES.contains(fieldType.getName())) {
      throw new IllegalStateException(
          "Invalid fieldType '" + fieldType + "' in property: " + property.getName());
    }
  }

  private void validateAnalyzer(PropertyDescriptor property) {
    String analyzer = property.getAnalyzer();
    if (analyzer != null && !ALLOWED_ANALYZERS.contains(analyzer)) {
      throw new IllegalStateException(
          "Invalid analyzer '" + analyzer + "' in property: " + property.getName());
    }
  }

  private void validateParser(PropertyDescriptor property) {
    String parser = property.getParser();
    if (parser != null && !ALLOWED_PARSERS.contains(parser)) {
      throw new IllegalStateException(
          "Invalid parser '" + parser + "' in property: " + property.getName());
    }
  }

  private void validateJsonPath(PropertyDescriptor property) {
    List<String> jsonPaths = property.getJsonPaths();
    if (jsonPaths != null) {
      try {
        // We can parse the JSONPath to validate syntax (Jayway JsonPath)
        jsonPaths.forEach(JsonPath::compile);

      } catch (InvalidPathException e) {
        throw new IllegalStateException(
            "Invalid jsonPaths '" + jsonPaths + "' in property: " + property.getName(), e);
      }
    }
  }
}
