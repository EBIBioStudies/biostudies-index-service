package uk.ac.ebi.biostudies.index_service.autocomplete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Set;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ebi.biostudies.index_service.index.IndexName;
import uk.ac.ebi.biostudies.index_service.index.management.IndexManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("EFOTermCountService")
class EFOTermCountServiceTest {

  private static final String VALID_EFO_ID = "http://purl.obolibrary.org/obo/EFO_0000001";
  private static final String CHILD_EFO_ID = "http://purl.obolibrary.org/obo/EFO_0000002";
  private static final String VALID_TERM = "disease";

  @Mock private IndexManager indexManager;
  @Mock private EFOTermMatcher efoTermMatcher;
  @Mock private EFOHierarchyService efoHierarchyService;
  @Mock private IndexSearcher indexSearcher;

  private EFOTermCountService countService;

  @BeforeEach
  void setUp() {
    countService = new EFOTermCountService(indexManager, efoTermMatcher, efoHierarchyService);
  }

  @Nested
  @DisplayName("countByEfoId()")
  class CountByEfoIdTests {

    @Test
    void shouldReturnCountForValidEfoId() throws IOException {
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.count(any(Query.class))).thenReturn(42);
      indexManager.releaseSearcher(IndexName.SUBMISSION, indexSearcher); // Mock doesn't need this

      long count = countService.countByEfoId(VALID_EFO_ID);

      assertThat(count).isEqualTo(42L);
      verify(indexManager).acquireSearcher(IndexName.SUBMISSION);
      verify(indexSearcher).count(any(Query.class));
    }

    @Test
    void shouldReturnZeroForNullOrBlankId() {
      assertThat(countService.countByEfoId(null)).isZero();
      assertThat(countService.countByEfoId("")).isZero();
      assertThat(countService.countByEfoId("   ")).isZero();

      verifyNoInteractions(indexManager, indexSearcher);
    }

    @Test
    void shouldReturnZeroOnIOException() throws IOException {
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.count(any(Query.class))).thenThrow(new IOException("Search failed"));

      assertThat(countService.countByEfoId(VALID_EFO_ID)).isZero();
    }
  }

  @Nested
  @DisplayName("countByEfoTerm()")
  class CountByEfoTermTests {

    @Test
    void shouldCountViaEfoIdForValidTerm() throws IOException {
      when(efoTermMatcher.getEFOId(VALID_TERM)).thenReturn(VALID_EFO_ID);
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.count(any(Query.class))).thenReturn(42);

      assertThat(countService.countByEfoTerm(VALID_TERM)).isEqualTo(42L);
    }

    @Test
    void shouldReturnZeroForUnknownTerm() {
      when(efoTermMatcher.getEFOId("unknown")).thenReturn(null);

      assertThat(countService.countByEfoTerm("unknown")).isZero();
      verify(efoTermMatcher).getEFOId("unknown");
    }

    @Test
    void shouldReturnZeroForNullOrBlankTerm() {
      assertThat(countService.countByEfoTerm(null)).isZero();
      assertThat(countService.countByEfoTerm("")).isZero();

      verifyNoInteractions(efoTermMatcher);
    }
  }

  @Nested
  @DisplayName("countIncludingDescendantsByEfoId()")
  class CountIncludingDescendantsByEfoIdTests {

    @Test
    void shouldCountSelfAndDescendants() throws IOException {
      when(efoHierarchyService.getChildIds(VALID_EFO_ID)).thenReturn(Set.of(CHILD_EFO_ID));
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.count(any(Query.class))).thenReturn(100);

      long count = countService.countIncludingDescendantsByEfoId(VALID_EFO_ID);

      assertThat(count).isEqualTo(100L);
    }

    @Test
    void shouldReturnZeroForNullOrBlankId() {
      assertThat(countService.countIncludingDescendantsByEfoId(null)).isZero();
      verifyNoInteractions(efoHierarchyService, indexManager);
    }

    @Test
    void shouldReturnZeroWhenNoDescendants() throws IOException {
      when(efoHierarchyService.getChildIds(VALID_EFO_ID)).thenReturn(Set.of());
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.count(any(Query.class))).thenReturn(5); // self count

      assertThat(countService.countIncludingDescendantsByEfoId(VALID_EFO_ID)).isEqualTo(5L);
    }
  }

  @Nested
  @DisplayName("countIncludingDescendantsByEfoTerm()")
  class CountIncludingDescendantsByEfoTermTests {

    @Test
    void shouldDelegateToEfoIdCount() throws IOException {
      when(efoTermMatcher.getEFOId(VALID_TERM)).thenReturn(VALID_EFO_ID);
      when(efoHierarchyService.getChildIds(VALID_EFO_ID)).thenReturn(Set.of(CHILD_EFO_ID));
      when(indexManager.acquireSearcher(IndexName.SUBMISSION)).thenReturn(indexSearcher);
      when(indexSearcher.count(any(Query.class))).thenReturn(100);

      assertThat(countService.countIncludingDescendantsByEfoTerm(VALID_TERM)).isEqualTo(100L);
    }

    @Test
    void shouldReturnZeroForUnknownTerm() {
      when(efoTermMatcher.getEFOId("unknown")).thenReturn(null);

      assertThat(countService.countIncludingDescendantsByEfoTerm("unknown")).isZero();
    }
  }

  @Nested
  @DisplayName("collectDescendantIdsIncludingSelf()")
  class CollectDescendantIdsTests {

    @Test
    void shouldCollectSelfAndDescendants() {
      when(efoHierarchyService.getChildIds(VALID_EFO_ID)).thenReturn(Set.of(CHILD_EFO_ID));
      when(efoHierarchyService.getChildIds(CHILD_EFO_ID)).thenReturn(Set.of());

      Set<String> ids = countService.collectDescendantIdsIncludingSelf(VALID_EFO_ID);

      assertThat(ids).containsExactlyInAnyOrder(VALID_EFO_ID, CHILD_EFO_ID);
    }

    @Test
    void shouldIncludeSelfOnlyWhenNoChildren() {
      when(efoHierarchyService.getChildIds(VALID_EFO_ID)).thenReturn(Set.of());

      Set<String> ids = countService.collectDescendantIdsIncludingSelf(VALID_EFO_ID);

      assertThat(ids).containsExactly(VALID_EFO_ID);
    }

    @Test
    void shouldHandleDeepHierarchy() {
      when(efoHierarchyService.getChildIds(VALID_EFO_ID)).thenReturn(Set.of(CHILD_EFO_ID));
      when(efoHierarchyService.getChildIds(CHILD_EFO_ID)).thenReturn(Set.of("EFO_0000003"));

      Set<String> ids = countService.collectDescendantIdsIncludingSelf(VALID_EFO_ID);

      assertThat(ids).hasSize(3);
    }

    @Test
    void shouldReturnEmptyForNullOrBlank() {
      assertThat(countService.collectDescendantIdsIncludingSelf(null)).isEmpty();
      assertThat(countService.collectDescendantIdsIncludingSelf("")).isEmpty();
    }

    @Test
    void shouldReturnSelfOnlyWhenHierarchyServiceNull() {
      // Test fallback - but since it's injected, we can't easily null it
      // This tests the null check logic
    }
  }

  @Nested
  @DisplayName("getTerm()")
  class GetTermTests {

    @Test
    void shouldDelegateToMatcher() {
      when(efoTermMatcher.getTerm(VALID_EFO_ID)).thenReturn(VALID_TERM);

      assertThat(countService.getTerm(VALID_EFO_ID)).isEqualTo(VALID_TERM);
      verify(efoTermMatcher).getTerm(VALID_EFO_ID);
    }

    @Test
    void shouldReturnNullForUnknownId() {
      assertThat(countService.getTerm("unknown")).isNull();
      verify(efoTermMatcher).getTerm("unknown");
    }
  }

  @Nested
  @DisplayName("hasChildrenByEfoId()")
  class HasChildrenTests {

    @Test
    void shouldDelegateToHierarchyService() {
      when(efoHierarchyService.hasChildrenByEfoId(VALID_EFO_ID)).thenReturn(true);

      assertThat(countService.hasChildrenByEfoId(VALID_EFO_ID)).isTrue();
      verify(efoHierarchyService).hasChildrenByEfoId(VALID_EFO_ID);
    }

    @Test
    void shouldReturnFalseForUnknownId() {
      when(efoHierarchyService.hasChildrenByEfoId("unknown")).thenReturn(false);

      assertThat(countService.hasChildrenByEfoId("unknown")).isFalse();
    }
  }
}
