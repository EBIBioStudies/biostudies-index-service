package uk.ac.ebi.biostudies.index_service.search.query;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;

/**
 * Custom query parser for BioStudies that:
 *
 * <ul>
 *   <li>Blocks queries on restricted fields (e.g., 'access' for security)
 *   <li>Handles numeric range queries for LONG-type fields
 *   <li>Uses field type metadata from IndexManager for proper query construction
 * </ul>
 */
@Slf4j
public class BioStudiesQueryParser extends MultiFieldQueryParser {

  private static final String ACCESS_FIELD = "access";
  private static final String FIELD_TYPE_ATTRIBUTE = "fieldType";
  private static final String LONG_TYPE = "long";

  private final IndexManager indexManager;
  private final CollectionRegistryService collectionRegistryService;

  /**
   * Constructs a BioStudies-specific query parser.
   *
   * @param fields fields to index
   * @param analyzer the analyzer to use for text processing
   * @param indexManager manager providing field type metadata
   */
  public BioStudiesQueryParser(
      String[] fields,
      Analyzer analyzer,
      IndexManager indexManager,
      CollectionRegistryService collectionRegistryService) {
    super(fields, analyzer);
    this.indexManager = indexManager;
    this.collectionRegistryService = collectionRegistryService;
  }

  /**
   * Parses a query string, blocking queries on restricted fields.
   *
   * @param query the query string to parse
   * @return parsed Lucene Query
   * @throws ParseException if query syntax is invalid or contains restricted fields
   */
  @Override
  public Query parse(String query) throws ParseException {
    if (containsRestrictedField(query, ACCESS_FIELD)) {
      log.warn("Blocked query attempting to use restricted field 'access': {}", query);
      throw new ParseException("Field 'access' not allowed in queries.");
    }
    return super.parse(query);
  }

  /**
   * Constructs range queries with proper numeric handling for LONG fields.
   *
   * @param field the field name
   * @param min minimum value (inclusive/exclusive based on startInclusive)
   * @param max maximum value (inclusive/exclusive based on endInclusive)
   * @param startInclusive whether minimum is inclusive
   * @param endInclusive whether maximum is inclusive
   * @return the appropriate range query for the field type
   * @throws ParseException if query construction fails
   */
  @Override
  protected Query getRangeQuery(
      String field, String min, String max, boolean startInclusive, boolean endInclusive)
      throws ParseException {

    if (isLongField(field)) {
      try {
        long minValue = parseLongValue(min, startInclusive, Long.MIN_VALUE);
        long maxValue = parseLongValue(max, endInclusive, Long.MAX_VALUE);

        log.debug(
            "Creating LongPoint range query for field '{}': [{}, {}]", field, minValue, maxValue);
        return LongPoint.newRangeQuery(field, minValue, maxValue);

      } catch (NumberFormatException e) {
        log.error("Invalid numeric value for LONG field '{}': min='{}', max='{}'", field, min, max);
        throw new ParseException("Invalid numeric value for field '" + field + "'");
      }
    }

    return super.getRangeQuery(field, min, max, startInclusive, endInclusive);
  }

  /** Checks if a query string contains a restricted field. */
  private boolean containsRestrictedField(String query, String fieldName) {
    // Add space at the start to avoid matching field names that end with the restricted name
    return (" " + query.toLowerCase()).contains(" " + fieldName + ":");
  }

  /** Checks if a field is of LONG type based on index metadata. */
  private boolean isLongField(String field) {
    PropertyDescriptor descriptor =
        collectionRegistryService.getCurrentRegistry().getGlobalPropertyRegistry().get(field);

    return descriptor != null && descriptor.getFieldType() == FieldType.LONG;
  }

  /** Parses a string value to long, handling wildcards and boundaries. */
  private long parseLongValue(String value, boolean inclusive, long defaultValue) {
    if (value == null || "*".equals(value)) {
      return defaultValue;
    }

    long parsed = Long.parseLong(value);

    // Adjust for exclusive boundaries
    if (!inclusive) {
      parsed = defaultValue == Long.MIN_VALUE ? parsed + 1 : parsed - 1;
    }

    return parsed;
  }
}
