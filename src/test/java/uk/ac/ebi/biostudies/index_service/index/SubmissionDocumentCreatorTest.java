package uk.ac.ebi.biostudies.index_service.index;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.TaxonomyManager;
import uk.ac.ebi.biostudies.index_service.autocomplete.EFOTermMatcher;
import uk.ac.ebi.biostudies.index_service.registry.model.*;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.service.CollectionRegistryService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionDocumentCreatorTest {

  @Mock private TaxonomyManager taxonomyManager;
  @Mock private CollectionRegistryService collectionRegistryService;
  @Mock private EFOTermMatcher efoTermMatcher;
  @Mock private CollectionRegistry collectionRegistry;
  @Mock private CollectionDescriptor collectionDescriptor;

  private SubmissionDocumentCreator creator;
  private Map<String, Object> valueMap;
  private List<PropertyDescriptor> testProperties;

  @BeforeEach
  void setUp() {
    creator = new SubmissionDocumentCreator(taxonomyManager, collectionRegistryService, efoTermMatcher);

    valueMap = new HashMap<>();
    valueMap.put(FieldName.FACET_COLLECTION.getName(), "test_collection");

    testProperties = createTestPropertyDescriptors();

    // Default mock behavior: no EFO terms found (most tests don't need EFO facets)
    when(efoTermMatcher.findEFOTerms(anyString())).thenReturn(Collections.emptyList());
  }

  @Test
  void createSubmissionDocument_nullValueMap_throwsNullPointerException() {
    assertThatThrownBy(() -> creator.createSubmissionDocument(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("valueMap cannot be null");
  }

  @Test
  void createSubmissionDocument_missingCollection_throwsIllegalArgumentException() {
    valueMap.remove(FieldName.FACET_COLLECTION.getName());

    assertThatThrownBy(() -> creator.createSubmissionDocument(valueMap))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Collection required: facet.collection");
  }

  @Test
  void createSubmissionDocument_happyPath_returnsFacetedDocument() throws IOException {
    // GIVEN
    valueMap.put(FieldName.FACET_COLLECTION.getName(), "test_collection");
    valueMap.put(Constants.CONTENT, "test content"); // Add content for EFO processing

    when(collectionRegistryService.getPublicAndCollectionRelatedProperties("test_collection".toLowerCase()))
        .thenReturn(Collections.emptyList());

    FacetsConfig facetsConfig = mock(FacetsConfig.class);
    when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);

    // Mock facetsConfig.build() to return the input document
    lenient().doAnswer(invocation -> invocation.getArgument(0))
        .when(facetsConfig).build(any(Document.class));

    // WHEN
    Document result = creator.createSubmissionDocument(valueMap);

    // THEN
    assertThat(result).isNotNull();
    verify(collectionRegistryService).getPublicAndCollectionRelatedProperties("test_collection");
    verify(taxonomyManager).getFacetsConfig();
    verify(efoTermMatcher).findEFOTerms("test content"); // Verify EFO processing called
  }

  @Test
  void createSubmissionDocument_fileParsingError_addsErrorField() throws IOException {
    // GIVEN
    valueMap.put(FieldName.FACET_COLLECTION.getName(), "testCollection");
    valueMap.put(Constants.HAS_FILE_PARSING_ERROR, true);
    valueMap.put(Constants.CONTENT, "test content");

    when(collectionRegistryService.getPublicAndCollectionRelatedProperties("testcollection"))
        .thenReturn(Collections.emptyList());

    FacetsConfig facetsConfig = mock(FacetsConfig.class);
    when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);

    lenient()
        .when(facetsConfig.build(any(Document.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // WHEN
    Document result = creator.createSubmissionDocument(valueMap);

    // THEN
    assertThat(result).isNotNull();
    IndexableField errorField = result.getField(Constants.HAS_FILE_PARSING_ERROR);
    assertThat(errorField).isNotNull();
    assertThat(errorField.stringValue()).isEqualTo("true");
  }

  @Test
  void createSubmissionDocument_withEFOTerms_addsFacetFields() throws IOException {
    // GIVEN
    valueMap.put(FieldName.FACET_COLLECTION.getName(), "test_collection");
    valueMap.put(Constants.CONTENT, "Study of odontoclast function");

    when(collectionRegistryService.getPublicAndCollectionRelatedProperties("test_collection"))
        .thenReturn(Collections.emptyList());

    // Mock EFO term matcher to find "odontoclast"
    when(efoTermMatcher.findEFOTerms("Study of odontoclast function"))
        .thenReturn(List.of("odontoclast"));

    // Mock ancestors: odontoclast → osteoclast → phagocyte
    when(efoTermMatcher.getAncestors("odontoclast"))
        .thenReturn(List.of("phagocyte", "osteoclast"));

    FacetsConfig facetsConfig = mock(FacetsConfig.class);
    when(taxonomyManager.getFacetsConfig()).thenReturn(facetsConfig);

    lenient()
        .when(facetsConfig.build(any(Document.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // WHEN
    Document result = creator.createSubmissionDocument(valueMap);

    // THEN
    assertThat(result).isNotNull();

    // Assert facet fields by TYPE, and validate facet DIMENSION via toString()
    List<SortedSetDocValuesFacetField> efoFacetFields =
        result.getFields().stream()
            .filter(f -> f instanceof SortedSetDocValuesFacetField)
            .map(f -> (SortedSetDocValuesFacetField) f)
            .filter(f -> f.toString().contains("dim=efo"))
            .toList();

    assertThat(efoFacetFields).isNotEmpty();
    assertThat(efoFacetFields.stream().map(Object::toString).toList())
        .anySatisfy(s -> assertThat(s).contains("path=phagocyte"));

    verify(efoTermMatcher).findEFOTerms("Study of odontoclast function");
    verify(efoTermMatcher).getAncestors("odontoclast");
  }

  @Test
  void addFieldToDocument_tokenizedString_addsTextField() {
    valueMap.put("tokenized_field", "test value");
    PropertyDescriptor prop = testProperties.get(0); // TOKENIZED_STRING
    Document doc = new Document();

    creator.addFieldToDocument(doc, valueMap, prop);

    List<Field> fields =
        Arrays.stream(doc.getFields("tokenized_field"))
            .map(f -> (Field) f)
            .collect(Collectors.toList());
    assertThat(fields).hasSize(1);

    TextField field = (TextField) fields.get(0);
    assertThat(field.stringValue()).isEqualTo("test value");
  }

  @Test
  void addFieldToDocument_tokenizedString_nullValue_addsNullString() {
    valueMap.put("tokenized_field", null);
    PropertyDescriptor prop = testProperties.get(0);
    Document doc = new Document();

    creator.addFieldToDocument(doc, valueMap, prop);

    List<Field> fields =
        Arrays.stream(doc.getFields("tokenized_field"))
            .map(f -> (Field) f)
            .collect(Collectors.toList());

    TextField field = (TextField) fields.get(0);
    assertThat(field.stringValue()).isEqualTo("null");
  }

  @Test
  void addFieldToDocument_unTokenizedString_missingKey_skips() {
    PropertyDescriptor prop = testProperties.get(1); // UNTOKENIZED_STRING
    Document doc = new Document();

    creator.addFieldToDocument(doc, valueMap, prop);

    assertThat(doc.getFields("un_tokenized_field")).isEmpty();
  }

  @Test
  void addFieldToDocument_unTokenizedString_withValue_addsFieldAndSort() {
    valueMap.put("un_tokenized_field", "sortable value");
    PropertyDescriptor prop = testProperties.get(1); // sortable=true
    Document doc = new Document();

    creator.addFieldToDocument(doc, valueMap, prop);

    List<Field> fields =
        Arrays.stream(doc.getFields("un_tokenized_field"))
            .map(f -> (Field) f)
            .collect(Collectors.toList());
    assertThat(fields).hasSize(2);

    // Main field (first)
    Field mainField = fields.get(0);
    assertThat(mainField.stringValue()).isEqualTo("sortable value");

    // Sort field (second)
    SortedDocValuesField sortField = (SortedDocValuesField) fields.get(1);
    BytesRef bytesRef = sortField.binaryValue();
    assertThat(bytesRef.utf8ToString()).isEqualTo("sortable value");
  }

  @Test
  void addFieldToDocument_long_validValue_addsThreeFields() {
    valueMap.put("long_field", "12345");
    PropertyDescriptor prop = testProperties.get(2); // LONG
    Document doc = new Document();

    creator.addFieldToDocument(doc, valueMap, prop);

    List<Field> fields =
        Arrays.stream(doc.getFields("long_field")).map(f -> (Field) f).collect(Collectors.toList());

    assertThat(fields).hasSize(3);

    assertThat(fields).anySatisfy(f -> assertThat(f).isInstanceOf(LongPoint.class));
    assertThat(fields)
        .anySatisfy(f -> assertThat(f).isInstanceOf(SortedNumericDocValuesField.class));
    assertThat(fields).anySatisfy(f -> assertThat(f).isInstanceOf(StoredField.class));
  }

  @Test
  void addFieldToDocument_long_nullValue_skips() {
    valueMap.put("long_field", null);
    PropertyDescriptor prop = testProperties.get(2);
    Document doc = new Document();

    creator.addFieldToDocument(doc, valueMap, prop);

    assertThat(doc.getFields("long_field")).isEmpty();
  }

  @Test
  void addFieldToDocument_long_emptyString_skips() {
    valueMap.put("long_field", "");
    PropertyDescriptor prop = testProperties.get(2);
    Document doc = new Document();

    creator.addFieldToDocument(doc, valueMap, prop);

    assertThat(doc.getFields("long_field")).isEmpty();
  }

  @Test
  void addFieldToDocument_long_invalidValue_handlesGracefully() {
    valueMap.put("long_field", "invalid");
    PropertyDescriptor prop = testProperties.get(2);
    Document doc = new Document();

    creator.addFieldToDocument(doc, valueMap, prop);

    assertThat(doc.getFields("long_field")).isEmpty(); // NumberFormatException caught
  }

  @Test
  void addFileAttributes_nullSet_addsDefaultString() {
    Document doc = new Document();

    creator.addFileAttributes(doc, null);

    StringField field = (StringField) doc.getField(Constants.FILE_ATTRIBUTE_NAMES);
    assertThat(field.stringValue()).isEqualTo("Name|Size|");
  }

  @Test
  void addFileAttributes_withColumns_buildsPipeDelimitedString() {
    Set<String> columns = new LinkedHashSet<>(); // Use LinkedHashSet for predictable order
    columns.add("col1");
    columns.add("col2");
    Document doc = new Document();

    creator.addFileAttributes(doc, columns);

    StringField field = (StringField) doc.getField(Constants.FILE_ATTRIBUTE_NAMES);
    String value = field.stringValue();

    // Check that it starts with "Name|Size|" and contains both columns
    assertThat(value).startsWith("Name|Size|");
    assertThat(value).contains("col1");
    assertThat(value).contains("col2");
  }

  private List<PropertyDescriptor> createTestPropertyDescriptors() {
    return List.of(
        // TOKENIZED_STRING
        PropertyDescriptor.builder()
            .name("tokenized_field")
            .fieldType(FieldType.TOKENIZED_STRING)
            .build(),

        // UNTOKENIZED_STRING (sortable)
        PropertyDescriptor.builder()
            .name("un_tokenized_field")
            .fieldType(FieldType.UNTOKENIZED_STRING)
            .sortable(true)
            .build(),

        // LONG
        PropertyDescriptor.builder().name("long_field").fieldType(FieldType.LONG).build(),

        // FACET
        PropertyDescriptor.builder().name("facet_field").fieldType(FieldType.FACET).build());
  }
}
