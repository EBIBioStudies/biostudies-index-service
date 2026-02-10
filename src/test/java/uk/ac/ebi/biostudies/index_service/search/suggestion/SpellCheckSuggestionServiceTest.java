package uk.ac.ebi.biostudies.index_service.search.suggestion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.spell.SuggestWord;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.Constants;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.efo.EFOField;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldName;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpellCheckSuggestionService Tests")
class SpellCheckSuggestionServiceTest {

  @Mock private IndexManager indexManager;
  @Mock private DirectSpellChecker spellChecker;
  @Mock private IndexSearcher submissionSearcher;
  @Mock private IndexSearcher efoSearcher;

  private SpellCheckSuggestionService service;

  private static ArgumentMatcher<Term> termFieldEquals(String expectedField) {
    return t -> t != null && expectedField.equals(t.field());
  }

  @BeforeEach
  void setUp() throws IOException {
    service = new SpellCheckSuggestionService(indexManager);
    service.setSpellChecker(spellChecker);

    lenient().when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(submissionSearcher);
    lenient().when(indexManager.acquireSearcher(IndexName.EFO)).thenReturn(efoSearcher);
  }

  private SuggestWord[] createSuggestWords(String... words) {
    SuggestWord[] suggestions = new SuggestWord[words.length];
    for (int i = 0; i < words.length; i++) {
      SuggestWord sw = new SuggestWord();
      sw.string = words[i];
      sw.score = 1.0f - (i * 0.1f);
      sw.freq = 10 - i;
      suggestions[i] = sw;
    }
    return suggestions;
  }

  @Nested
  @DisplayName("Initialization Tests")
  class InitializationTests {

    @Test
    @DisplayName("Should report unavailable when spell checker not initialized")
    void shouldReportUnavailableWhenNotInitialized() {
      SpellCheckSuggestionService uninitializedService =
          new SpellCheckSuggestionService(indexManager);
      assertFalse(uninitializedService.isAvailable());
    }

    @Test
    @DisplayName("Should report available after spell checker set")
    void shouldReportAvailableWhenInitialized() {
      assertTrue(service.isAvailable());
    }

    @Test
    @DisplayName("Should throw exception when suggesting without initialization")
    void shouldThrowWhenSuggestingWithoutInitialization() {
      SpellCheckSuggestionService uninitializedService =
          new SpellCheckSuggestionService(indexManager);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class,
              () -> uninitializedService.suggestSimilar("query", 5));

      assertEquals("Spell checker not initialized", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Input Validation Tests")
  class InputValidationTests {

    @Test
    @DisplayName("Should reject null query string")
    void shouldRejectNullQueryString() {
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> service.suggestSimilar(null, 5));
      assertEquals("Query string must not be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should reject empty query string")
    void shouldRejectEmptyQueryString() {
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> service.suggestSimilar("", 5));
      assertEquals("Query string must not be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should reject blank query string")
    void shouldRejectBlankQueryString() {
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> service.suggestSimilar("   ", 5));
      assertEquals("Query string must not be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should accept valid query strings")
    void shouldAcceptValidQueryString() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenReturn(new SuggestWord[0]);

      assertDoesNotThrow(() -> service.suggestSimilar("osteoclast", 5));
    }
  }

  @Nested
  @DisplayName("Accession Pattern Detection Tests")
  class AccessionPatternTests {

    @Test
    @DisplayName("Should detect S-BSST format as accession")
    void shouldDetectSBSSTFormat() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (FieldName.ACCESSION.getName().equals(term.field())) {
              return createSuggestWords("S-BSST1432");
            }
            return new SuggestWord[0];
          });

      String[] results = service.suggestSimilar("S-BSST143", 5);

      assertEquals(1, results.length);
      assertEquals("S-BSST1432", results[0]);
    }

    @Test
    @DisplayName("Should detect E-MTAB format as accession")
    void shouldDetectEMTABFormat() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (FieldName.ACCESSION.getName().equals(term.field())) {
              return createSuggestWords("E-MTAB-1234");
            }
            return new SuggestWord[0];
          });

      String[] results = service.suggestSimilar("E-MTAB-123", 5);

      assertEquals(1, results.length);
      assertEquals("E-MTAB-1234", results[0]);
    }

    @Test
    @DisplayName("Should not treat regular words as accessions")
    void shouldNotTreatRegularWordsAsAccessions() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (EFOField.TERM.getFieldName().equals(term.field())) {
              return createSuggestWords("osteoclast");
            }
            return new SuggestWord[0];
          });

      service.suggestSimilar("osteoclastt", 5);

      verify(indexManager).acquireSearcher(IndexName.EFO);
    }
  }

  @Nested
  @DisplayName("Cascading Fallback Tests")
  class CascadingFallbackTests {

    @Test
    @DisplayName("Should return EFO suggestions and skip submission index")
    void shouldReturnEFOSuggestionsFirst() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (EFOField.TERM.getFieldName().equals(term.field())) {
              return createSuggestWords("osteoclast");
            }
            return new SuggestWord[0];
          });

      String[] results = service.suggestSimilar("osteoclastt", 5);

      assertEquals(1, results.length);
      assertEquals("osteoclast", results[0]);
      verify(indexManager).acquireSearcher(IndexName.EFO);
      verify(indexManager, never()).acquireSearcher(IndexName.SUBMISSION);
    }

    @Test
    @DisplayName("Should fallback to submission content when EFO returns no results")
    void shouldFallbackToSubmissionContent() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (Constants.CONTENT.equals(term.field())) {
              return createSuggestWords("melanogaster");
            }
            return new SuggestWord[0];
          });

      String[] results = service.suggestSimilar("melanogaste", 5);

      assertEquals(1, results.length);
      assertEquals("melanogaster", results[0]);
      verify(indexManager).acquireSearcher(IndexName.EFO);
      verify(indexManager).acquireSearcher(IndexName.SUBMISSION);
    }

    @Test
    @DisplayName("Should return empty array when no suggestions found in any index")
    void shouldReturnEmptyWhenNoSuggestionsFound() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenReturn(new SuggestWord[0]);

      String[] results = service.suggestSimilar("xyzabc", 5);

      assertEquals(0, results.length);
    }

    @Test
    @DisplayName("Should fallback from accession to EFO to content")
    void shouldCascadeThroughAllLevelsForAccessionFormat() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (Constants.CONTENT.equals(term.field())) {
              return createSuggestWords("S-BSST9999");
            }
            return new SuggestWord[0];
          });

      String[] results = service.suggestSimilar("S-BSST999", 5);

      assertEquals(1, results.length);
      assertEquals("S-BSST9999", results[0]);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should return empty array when IOException occurs during searcher acquisition")
    void shouldHandleIOExceptionDuringAcquisition() throws IOException {
      when(indexManager.acquireSearcher(any())).thenThrow(new IOException("Index unavailable"));

      String[] results = service.suggestSimilar("query", 5);

      assertEquals(0, results.length);
    }

    @Test
    @DisplayName("Should return empty array when IOException occurs during spell check")
    void shouldHandleIOExceptionDuringSpellCheck() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenThrow(new IOException("Spell check failed"));

      String[] results = service.suggestSimilar("query", 5);

      assertEquals(0, results.length);
    }
  }

  @Nested
  @DisplayName("Field Name Resolution Tests")
  class FieldNameResolutionTests {

    @Test
    @DisplayName("Should use FieldName enum for accession field")
    void shouldUseFieldNameEnumForAccession() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (FieldName.ACCESSION.getName().equals(term.field())) {
              return createSuggestWords("S-BSST1432");
            }
            return new SuggestWord[0];
          });

      service.suggestSimilar("S-BSST143", 5);

      verify(spellChecker)
          .suggestSimilar(argThat(termFieldEquals(FieldName.ACCESSION.getName())),
              anyInt(), any(), any());
    }

    @Test
    @DisplayName("Should use EFOField enum for EFO term field")
    void shouldUseEFOFieldEnumForTerm() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (EFOField.TERM.getFieldName().equals(term.field())) {
              return createSuggestWords("osteoclast");
            }
            return new SuggestWord[0];
          });

      service.suggestSimilar("osteoclastt", 5);

      verify(spellChecker)
          .suggestSimilar(argThat(termFieldEquals(EFOField.TERM.getFieldName())),
              anyInt(), any(), any());
    }

    @Test
    @DisplayName("Should use Constants for content field")
    void shouldUseConstantsForContentField() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenAnswer(invocation -> {
            Term term = invocation.getArgument(0);
            if (Constants.CONTENT.equals(term.field())) {
              return createSuggestWords("melanogaster");
            }
            return new SuggestWord[0];
          });

      service.suggestSimilar("melanogaste", 5);

      verify(spellChecker)
          .suggestSimilar(argThat(termFieldEquals(Constants.CONTENT)), anyInt(), any(), any());
    }
  }

  @Nested
  @DisplayName("BytesRef Conversion Tests")
  class BytesRefConversionTests {

    @Test
    @DisplayName("Should correctly convert query string to BytesRef")
    void shouldConvertQueryStringToBytesRef() throws IOException {
      when(spellChecker.suggestSimilar(any(Term.class), anyInt(), any(), any()))
          .thenReturn(createSuggestWords("osteoclast"));

      service.suggestSimilar("osteoclastt", 5);

      verify(spellChecker)
          .suggestSimilar(
              argThat(term -> {
                if (term == null) return false;
                BytesRef expectedBytes = new BytesRef("osteoclastt");
                return expectedBytes.equals(term.bytes());
              }),
              anyInt(), any(), any());
    }
  }
}
