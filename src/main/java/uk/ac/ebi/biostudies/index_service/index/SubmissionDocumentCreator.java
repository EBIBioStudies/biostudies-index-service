package uk.ac.ebi.biostudies.index_service.index;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;
import uk.ac.ebi.biostudies.index_service.registry.model.LuceneFieldTypes;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;

/**
 * Creates Lucene {@link Document}s for BioStudies submissions from parsed value maps.
 *
 * <p>This component:
 *
 * <ul>
 *   <li>Extracts collection name from value map to lookup field descriptors
 *   <li>Adds special fields (file attributes, parsing errors)
 *   <li>Processes each collection field according to its {@link PropertyDescriptor}
 *   <li>Builds faceted document using taxonomy configuration
 * </ul>
 *
 * <p>Null values and missing fields are handled per field type specification:
 *
 * <ul>
 *   <li>TOKENIZED_STRING: Always indexed (null → "null")
 *   <li>UNTOKENIZED_STRING: Skipped if key missing
 *   <li>LONG: Skipped if missing/null/empty; parses String → long
 *   <li>FACET: Null → special NA/default handling
 * </ul>
 */
@Slf4j
@Component
public class SubmissionDocumentCreator {

  private final TaxonomyManager taxonomyManager;
  private final CollectionRegistryService collectionRegistryService;

  /**
   * Constructs a document creator with required indexing dependencies.
   *
   * @param taxonomyManager provides facet configuration and taxonomy writer
   * @param collectionRegistryService provides collection field descriptors
   */
  public SubmissionDocumentCreator(
      TaxonomyManager taxonomyManager, CollectionRegistryService collectionRegistryService) {
    this.taxonomyManager = Objects.requireNonNull(taxonomyManager);
    this.collectionRegistryService = Objects.requireNonNull(collectionRegistryService);
  }

  /**
   * Creates a fully-formed Lucene Document for submission indexing.
   *
   * <p>Workflow:
   *
   * <ol>
   *   <li>Validates collection field presence
   *   <li>Adds file attributes and parsing error flags
   *   <li>Processes all collection-specific fields via {@link #addFieldToDocument}
   *   <li>Builds final faceted document via {@link org.apache.lucene.facet.FacetsConfig}
   * </ol>
   *
   * @param valueMap parsed submission values from {@link SubmissionParser}
   * @return ready-to-index Lucene Document with facets
   * @throws IOException if facet building fails
   * @throws IllegalArgumentException if collection field missing from valueMap
   * @throws NullPointerException if valueMap is null
   */
  public Document createSubmissionDocument(Map<String, Object> valueMap) throws IOException {
    Objects.requireNonNull(valueMap, "valueMap cannot be null");

    String collection = getCollection(valueMap);
    if (collection == null) {
      log.warn(
          "Missing collection field '{}', skipping document creation",
          FieldName.FACET_COLLECTION.getName());
      throw new IllegalArgumentException(
          "Collection required: " + FieldName.FACET_COLLECTION.getName());
    }

    Document doc = new Document();

    // Add special submission-level fields
    addFileAttributes(doc, (Set<String>) valueMap.get(Constants.FILE_ATTRIBUTE_NAMES));
    if (valueMap.get(Constants.HAS_FILE_PARSING_ERROR) != null) {
      doc.add(new TextField(Constants.HAS_FILE_PARSING_ERROR, "true", Field.Store.YES));
    }

    // Process public and collection-specific properties
    List<PropertyDescriptor> relatedProperties =
        collectionRegistryService.getPublicAndCollectionRelatedProperties(collection.toLowerCase());
    for (PropertyDescriptor propertyDescriptor : relatedProperties) {
      addFieldToDocument(doc, valueMap, propertyDescriptor);
    }

    // Build final faceted document
    return taxonomyManager.getFacetsConfig().build(doc);
  }

  /**
   * Extracts collection name from value map.
   *
   * @param valueMap parsed submission values
   * @return collection name or null if missing/not String
   */
  private String getCollection(Map<String, Object> valueMap) {
    Object collectionObj = valueMap.get(FieldName.FACET_COLLECTION.getName());
    return collectionObj instanceof String ? (String) collectionObj : null;
  }

  /**
   * Adds single field to document according to its {@link PropertyDescriptor} specification.
   * Package-private for unit testing.
   *
   * <p>Null handling per field type:
   *
   * <ul>
   *   <li>TOKENIZED_STRING: Always adds (null → "null")
   *   <li>UNTOKENIZED_STRING: Skips if key missing
   *   <li>LONG: Skips if missing/null/empty; String → long parsing
   *   <li>FACET: Passes null to {@link #addFacet} for NA/default handling
   * </ul>
   *
   * @param doc target Lucene document
   * @param valueMap parsed submission values (String-typed from new parser)
   * @param prop field descriptor defining type/indexing behavior
   */
  void addFieldToDocument(Document doc, Map<String, Object> valueMap, PropertyDescriptor prop) {
    String fieldName = prop.getName();

    try {
      switch (prop.getFieldType()) {
        case TOKENIZED_STRING:
          // Always index (null → "null" string)
          doc.add(
              new TextField(fieldName, String.valueOf(valueMap.get(fieldName)), Field.Store.YES));
          break;

        case UNTOKENIZED_STRING:
          if (!valueMap.containsKey(fieldName)) break;
          String untokenizedValue = String.valueOf(valueMap.get(fieldName));
          Field field =
              new Field(fieldName, untokenizedValue, LuceneFieldTypes.NOT_ANALYZED_STORED);
          doc.add(field);
          if (Boolean.TRUE.equals(prop.getSortable())) {
            doc.add(new SortedDocValuesField(fieldName, new BytesRef(untokenizedValue)));
          }
          break;

        case LONG:
          Object rawValue = valueMap.get(fieldName);
          if (!valueMap.containsKey(fieldName)
              || rawValue == null
              || StringUtils.isEmpty(rawValue.toString())) break;
          long longValue = Long.parseLong(rawValue.toString());
          doc.add(new SortedNumericDocValuesField(fieldName, longValue));
          doc.add(new StoredField(fieldName, rawValue.toString()));
          doc.add(new LongPoint(fieldName, longValue));
          break;

        case FACET:
          // Null handling delegated to addFacet (NA/defaults)
          String facetValue =
              valueMap.containsKey(fieldName) && valueMap.get(fieldName) != null
                  ? String.valueOf(valueMap.get(fieldName))
                  : null;
          addFacet(facetValue, fieldName, doc, prop);
          break;

        default:
          log.debug("Unsupported field type '{}' for field '{}'", prop.getFieldType(), fieldName);
      }
    } catch (Exception e) {
      log.warn("Failed to add field '{}': {}", fieldName, e.getMessage(), e);
    }
  }

  /**
   * Adds pipe-delimited file attribute summary to document.
   *
   * <p>Format: "Name|Size|col1|col2|..." for UI display. Null/empty sets produce "Name|Size|".
   *
   * @param doc target document
   * @param columnAtts discovered file columns or null
   */
  void addFileAttributes(Document doc, Set<String> columnAtts) {
    StringBuilder allAtts = new StringBuilder("Name|Size|");
    if (columnAtts == null) columnAtts = new HashSet<>();
    for (String att : columnAtts) allAtts.append(att).append("|");
    doc.add(new StringField(Constants.FILE_ATTRIBUTE_NAMES, allAtts.toString(), Field.Store.YES));
  }

  /**
   * Adds facet fields with multi-value, null, and default value handling.
   *
   * <p>Null/empty/"null" values:
   *
   * <ul>
   *   <li>Skip fileType/linkType/boolean facets entirely
   *   <li>Other facets → {@link Constants#NA} or descriptor default
   * </ul>
   *
   * Pipe-delimited values split into multiple {@link FacetField}s.
   *
   * @param value raw facet value (null allowed)
   * @param fieldName facet field name
   * @param doc target document
   * @param propertyDescriptor facet configuration
   */
  private void addFacet(
      String value, String fieldName, Document doc, PropertyDescriptor propertyDescriptor) {
    if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")) {
      // Skip specific facet types entirely
      if (fieldName.equalsIgnoreCase(FieldName.FACET_FILE_TYPE.toString())
          || fieldName.equalsIgnoreCase(FieldName.FACET_LINK_TYPE.toString())
          || (propertyDescriptor.isFacet()
              && "boolean".equalsIgnoreCase(propertyDescriptor.getFacetType()))) {
        return;
      }
      // Use NA or descriptor default
      value = Constants.NA;
    }

    boolean mustLowerCase = propertyDescriptor.isToLowerCase();
    for (String subVal : StringUtils.split(value, Constants.FACET_VALUE_DELIMITER)) {
      if (subVal == null || subVal.trim().isEmpty()) continue;

      // Override NA with descriptor default if available
      if (subVal.equalsIgnoreCase(Constants.NA) && propertyDescriptor.getDefaultValue() != null) {
        subVal = propertyDescriptor.getDefaultValue();
      }

      String finalValue = mustLowerCase ? subVal.trim().toLowerCase() : subVal.trim();
      // doc.add(new FacetField(fieldName, finalValue));
      doc.add(new SortedSetDocValuesFacetField(fieldName, finalValue));
      log.debug("Adding facet field '{}': {}", fieldName, subVal);
    }
  }
}
