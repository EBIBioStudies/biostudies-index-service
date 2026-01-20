package uk.ac.ebi.biostudies.index_service.index;

import static uk.ac.ebi.biostudies.index_service.Constants.PUBLIC;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.parsing.JsonPathService;
import uk.ac.ebi.biostudies.index_service.parsing.Parser;
import uk.ac.ebi.biostudies.index_service.parsing.ParserManager;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;

/**
 * Parses submission JSON data into a structured map for indexing.
 *
 * <p>This class extracts and transforms submission data from its JSON representation into key-value
 * pairs suitable for Lucene indexing. It applies collection-specific property descriptors and
 * parsers to extract values from the submission JSON using JSONPath expressions and custom parsing
 * logic.
 */
@Slf4j
@Component
public class SubmissionParser {

  private final CollectionRegistryService collectionRegistryService;
  private final ParserManager parserManager;
  private final JsonPathService jsonPathService;

  public SubmissionParser(
      CollectionRegistryService collectionRegistryService,
      ParserManager parserManager,
      JsonPathService jsonPathService) {
    this.collectionRegistryService = collectionRegistryService;
    this.parserManager = parserManager;
    this.jsonPathService = jsonPathService;
  }

  /**
   * Parses a submission JSON node into a map of indexable fields.
   *
   * <p>This method processes the submission in two stages:
   *
   * <ol>
   *   <li>Extracts common "public" fields that apply to all submissions.
   *   <li>If the submission is not a collection, applies additional collection-specific parsing.
   * </ol>
   *
   * @param submission the submission JSON node to parse; must not be null
   * @return a mutable map where keys are field names and values are extracted and parsed data
   * @throws NullPointerException if the submission is null
   */
  public Map<String, Object> parseSubmission(JsonNode submission) {
    Objects.requireNonNull(submission, "Submission must not be null");

    Map<String, Object> valueMap = new HashMap<>();
    CollectionRegistry collectionRegistry = collectionRegistryService.getCurrentRegistry();

    parsePublicProperties(submission, collectionRegistry, valueMap);

    if (isSubmissionACollection(valueMap)) {
      return valueMap;
    }

    // Normalize facets before collection-specific parsing
    var normalizedFacets = normalizeCollectionFacets(valueMap);

    parseCollectionsSpecificProperties(submission, normalizedFacets, collectionRegistry, valueMap);

    return valueMap;
  }

  /**
   * Parses and populates public properties from the submission into the provided map.
   *
   * <p>Public properties are defined as those not marked private and present in the "public"
   * collection descriptor. For each property, a configured parser extracts the value.
   *
   * @param submission the JSON submission node; must not be null
   * @param collectionRegistry the registry to resolve property descriptors; must not be null
   * @param valueMap mutable map to populate with parsed properties; must not be null
   * @throws IllegalStateException if a parser is not found for any property
   */
  private void parsePublicProperties(
      JsonNode submission, CollectionRegistry collectionRegistry, Map<String, Object> valueMap) {

    Objects.requireNonNull(submission, "submission must not be null");
    Objects.requireNonNull(collectionRegistry, "collectionRegistry must not be null");
    Objects.requireNonNull(valueMap, "valueMap must not be null");

    List<PropertyDescriptor> publicProperties = getPublicProperties(collectionRegistry);

    for (PropertyDescriptor propertyDescriptor : publicProperties) {
      Parser parser = parserManager.getParser(propertyDescriptor.getName());
      if (parser == null) {
        throw new IllegalStateException(
            "Parser not found for public property " + propertyDescriptor.getName());
      }
      String value = parser.parse(submission, propertyDescriptor, jsonPathService);
      updateValueMap(value, valueMap, propertyDescriptor);
    }
  }

  /**
   * Parses and populates collection-specific properties into the map for the given submission.
   *
   * <p>For each collection name in {@code collections}, this method looks up its descriptor,
   * iterates over its properties, extracts values via configured parsers, and updates {@code
   * valueMap}. Collections without descriptors will be skipped with a warning log.
   *
   * @param submission the submission JSON node; must not be null
   * @param collections set of collection names; must not be null
   * @param collectionRegistry registry for resolving collection descriptors; must not be null
   * @param valueMap mutable map to populate; must not be null
   * @throws IllegalStateException if a parser is not found for any property
   */
  private void parseCollectionsSpecificProperties(
      JsonNode submission,
      Set<String> collections,
      CollectionRegistry collectionRegistry,
      Map<String, Object> valueMap) {

    Objects.requireNonNull(submission, "submission must not be null");
    Objects.requireNonNull(collections, "collections must not be null");
    Objects.requireNonNull(collectionRegistry, "collectionRegistry must not be null");
    Objects.requireNonNull(valueMap, "valueMap must not be null");

    for (String collection : collections) {
      CollectionDescriptor collectionDescriptor =
          collectionRegistry.getCollectionDescriptor(collection);
      if (collectionDescriptor == null) {
        log.debug("Skipping collection {} as it does not have specific configuration", collection);
        continue;
      }

      for (PropertyDescriptor propertyDescriptor : collectionDescriptor.getProperties()) {
        Parser parser = parserManager.getParser(propertyDescriptor.getName());
        if (parser == null) {
          throw new IllegalStateException(
              "Parser not found for property " + propertyDescriptor.getName());
        }

        String value = parser.parse(submission, propertyDescriptor, jsonPathService);
        updateValueMap(value, valueMap, propertyDescriptor);
      }
    }
  }

  /**
   * Centralizes logic to process and update the value map.
   *
   * <p>The parsed value is post-processed (e.g., boolean facet normalization) before being stored.
   *
   * @param parsedValue raw value extracted by the parser, may be null or blank
   * @param valueMap map to update with processed value
   * @param propertyDescriptor metadata describing the property being stored
   */
  private void updateValueMap(
      String parsedValue, Map<String, Object> valueMap, PropertyDescriptor propertyDescriptor) {
    String processedValue = processParserValue(parsedValue, propertyDescriptor);
    if (shouldIgnoreFalseBooleanFacet(propertyDescriptor, processedValue)) {
      return;
    }
    valueMap.put(propertyDescriptor.getName(), processedValue);
  }

  private boolean shouldIgnoreFalseBooleanFacet(PropertyDescriptor pd, String value) {
    return FieldType.FACET.equals(pd.getFieldType())
        && "boolean".equalsIgnoreCase(pd.getFacetType())
        && "false".equalsIgnoreCase(value);
  }

  /**
   * Processes raw parsed values according to field and facet types.
   *
   * <p>Specifically, for boolean facet types, normalizes null or blank values to "false", and any
   * non-blank value to "true". All other values are returned as-is.
   *
   * @param parsedValue the raw parsed value, may be null or blank
   * @param propertyDescriptor property metadata used to determine processing rules
   * @return normalized value string suitable for indexing
   */
  private String processParserValue(String parsedValue, PropertyDescriptor propertyDescriptor) {
    if (FieldType.FACET.equals(propertyDescriptor.getFieldType())
        && "boolean".equalsIgnoreCase(propertyDescriptor.getFacetType())) {
      return (parsedValue == null || parsedValue.isBlank()) ? "false" : "true";
    }
    return parsedValue;
  }

  /**
   * Normalizes collection facet values by removing duplicates, empty values, and reserved keywords.
   *
   * <p>Updates the value map with a normalized facet string joined by the configured delimiter.
   *
   * @param valueMap map containing submission values, expected to contain the facet collection
   *     field
   * @return the set of unique, normalized facet strings, or empty if none present
   */
  private Set<String> normalizeCollectionFacets(Map<String, Object> valueMap) {
    String collectionFieldName = FieldName.FACET_COLLECTION.getName();

    if (!valueMap.containsKey(collectionFieldName)) {
      return Collections.emptySet();
    }

    Object collectionValue = valueMap.get(collectionFieldName);
    if (collectionValue == null) {
      return Collections.emptySet();
    }

    String[] facetValues =
        collectionValue
            .toString()
            .toLowerCase()
            .split(Pattern.quote(Constants.FACET_VALUE_DELIMITER));

    Set<String> uniqueFacets =
        Arrays.stream(facetValues)
            .map(String::trim)
            .filter(facet -> !facet.isEmpty() && !PUBLIC.equalsIgnoreCase(facet))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    valueMap.put(collectionFieldName, String.join(Constants.FACET_VALUE_DELIMITER, uniqueFacets));

    return uniqueFacets;
  }

  /**
   * Determines whether a submission is a collection based on the parsed "type" field value.
   *
   * <p>This distinction affects whether collection-specific properties need further parsing or not.
   *
   * @param valueMap map of parsed submission fields, must contain the type field
   * @return true if the submission type matches the collection type constant, false otherwise
   * @throws IllegalArgumentException if the type field is missing
   * @throws NullPointerException if the type field value is null
   * @throws ClassCastException if the type field value is not a String
   */
  private boolean isSubmissionACollection(Map<String, Object> valueMap) {
    if (!valueMap.containsKey(FieldName.TYPE.getName())) {
      throw new IllegalArgumentException(
          "Submission does not have a parsed value for [" + FieldName.TYPE.getName() + "]");
    }
    String type = (String) valueMap.get(FieldName.TYPE.getName());
    return type.equalsIgnoreCase(Constants.COLLECTION_TYPE);
  }

  /**
   * Retrieves the property descriptors from the public collection descriptor.
   *
   * <p>The public collection contains base properties applicable to all submissions.
   *
   * @param collectionRegistry the registry containing collection descriptors
   * @return list of property descriptors for the public collection or empty list if none defined
   */
  private List<PropertyDescriptor> getPublicProperties(CollectionRegistry collectionRegistry) {
    List<PropertyDescriptor> publicProperties = new ArrayList<>();
    if (collectionRegistry.containsCollection(PUBLIC)) {
      CollectionDescriptor collectionDescriptor =
          collectionRegistry.getCollectionDescriptor(PUBLIC);
      publicProperties = collectionDescriptor.getProperties();
    }
    return publicProperties;
  }
}
