package uk.ac.ebi.biostudies.index_service.autocomplete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EFOHierarchyService")
class EFOHierarchyServiceTest {

  private static final String CELL_ID = "http://purl.obolibrary.org/obo/CL_0000000";
  private static final String LEUKOCYTE_ID = "http://purl.obolibrary.org/obo/CL_0000738";
  private static final String ODONTOCLAST_ID = "http://purl.obolibrary.org/obo/CL_0002452";

  @Mock private EFOTermMatcher efoTermMatcher;

  private EFOHierarchyService hierarchyService;

  @BeforeEach
  void setUp() {
    hierarchyService = new EFOHierarchyService(efoTermMatcher);
  }

  @Nested
  @DisplayName("getTerm()")
  class GetTermTests {

    @Test
    void shouldReturnPrimaryTermForEfoId() {
      when(efoTermMatcher.getTerm(CELL_ID)).thenReturn("cell");

      assertThat(hierarchyService.getTerm(CELL_ID)).isEqualTo("cell");
      verify(efoTermMatcher).getTerm(CELL_ID);
    }

    @Test
    void shouldReturnNullForUnknownEfoId() {
      // Mock defaults to null - no stub needed
      assertThat(hierarchyService.getTerm("http://unknown/id")).isNull();
      verify(efoTermMatcher).getTerm("http://unknown/id");
    }

    @Test
    void shouldReturnNullForNullOrBlankId() {
      assertThat(hierarchyService.getTerm(null)).isNull();
      assertThat(hierarchyService.getTerm("")).isNull();
      assertThat(hierarchyService.getTerm("   ")).isNull();

      verify(efoTermMatcher, never()).getTerm(null);
    }
  }

  @Nested
  @DisplayName("getAncestors()")
  class GetAncestorsTests {

    @Test
    void shouldReturnAncestorsForTerm() {
      when(efoTermMatcher.getAncestors("odontoclast"))
          .thenReturn(List.of("cell", "leukocyte", "myeloid leukocyte", "osteoclast"));

      assertThat(hierarchyService.getAncestors("odontoclast"))
          .containsExactly("cell", "leukocyte", "myeloid leukocyte", "osteoclast");
      verify(efoTermMatcher).getAncestors("odontoclast");
    }

    @Test
    void shouldReturnEmptyListForUnknownTerm() {
      // Mock defaults to empty list - no stub needed
      assertThat(hierarchyService.getAncestors("unknown")).isEmpty();
      verify(efoTermMatcher).getAncestors("unknown");
    }

    @Test
    void shouldReturnEmptyListForNullOrBlankTerm() {
      assertThat(hierarchyService.getAncestors(null)).isEmpty();
      assertThat(hierarchyService.getAncestors("")).isEmpty();
      assertThat(hierarchyService.getAncestors("   ")).isEmpty();

      verify(efoTermMatcher, never()).getAncestors(null);
    }
  }

  @Nested
  @DisplayName("getAncestorsByEfoId()")
  class GetAncestorsByEfoIdTests {

    @Test
    void shouldResolveIdAndReturnAncestors() {
      when(efoTermMatcher.getTerm(ODONTOCLAST_ID)).thenReturn("odontoclast");
      when(efoTermMatcher.getAncestors("odontoclast"))
          .thenReturn(List.of("cell", "leukocyte", "myeloid leukocyte", "osteoclast"));

      assertThat(hierarchyService.getAncestorsByEfoId(ODONTOCLAST_ID))
          .containsExactly("cell", "leukocyte", "myeloid leukocyte", "osteoclast");
    }

    @Test
    void shouldReturnEmptyListWhenIdUnknown() {
      // Mock defaults to null - no stub needed
      assertThat(hierarchyService.getAncestorsByEfoId("http://unknown/id")).isEmpty();
      verify(efoTermMatcher).getTerm("http://unknown/id");
    }

    @Test
    void shouldReturnEmptyListForNullOrBlankId() {
      assertThat(hierarchyService.getAncestorsByEfoId(null)).isEmpty();
      assertThat(hierarchyService.getAncestorsByEfoId("")).isEmpty();
      assertThat(hierarchyService.getAncestorsByEfoId("   ")).isEmpty();
    }
  }

  @Nested
  @DisplayName("getChildIds()")
  class GetChildIdsTests {

    @Test
    void shouldReturnChildIdsFromMatcher() {
      when(efoTermMatcher.getChildIds(CELL_ID)).thenReturn(Set.of(LEUKOCYTE_ID, ODONTOCLAST_ID));

      assertThat(hierarchyService.getChildIds(CELL_ID))
          .containsExactlyInAnyOrder(LEUKOCYTE_ID, ODONTOCLAST_ID);

      verify(efoTermMatcher).getChildIds(CELL_ID);
    }

    @Test
    void shouldReturnEmptySetWhenNoChildrenExist() {
      when(efoTermMatcher.getChildIds(CELL_ID)).thenReturn(Set.of());

      assertThat(hierarchyService.getChildIds(CELL_ID)).isEmpty();
      verify(efoTermMatcher).getChildIds(CELL_ID);
    }

    @Test
    void shouldReturnEmptySetForNullOrBlankId() {
      assertThat(hierarchyService.getChildIds(null)).isEmpty();
      assertThat(hierarchyService.getChildIds("")).isEmpty();
      assertThat(hierarchyService.getChildIds("   ")).isEmpty();

      verifyNoInteractions(efoTermMatcher);
    }
  }

  @Nested
  @DisplayName("getChildren() / getChildrenByEfoId()")
  class GetChildrenTests {

    @Test
    void shouldReturnChildTermsForTerm() {
      when(efoTermMatcher.getEFOId("cell")).thenReturn(CELL_ID);
      when(efoTermMatcher.getChildIds(CELL_ID)).thenReturn(Set.of(LEUKOCYTE_ID, ODONTOCLAST_ID));
      when(efoTermMatcher.getTerm(LEUKOCYTE_ID)).thenReturn("leukocyte");
      when(efoTermMatcher.getTerm(ODONTOCLAST_ID)).thenReturn("odontoclast");

      assertThat(hierarchyService.getChildren("cell"))
          .containsExactlyInAnyOrder("leukocyte", "odontoclast");
    }

    @Test
    void shouldReturnChildTermsForEfoId() {
      when(efoTermMatcher.getChildIds(CELL_ID)).thenReturn(Set.of(LEUKOCYTE_ID));
      when(efoTermMatcher.getTerm(LEUKOCYTE_ID)).thenReturn("leukocyte");

      assertThat(hierarchyService.getChildrenByEfoId(CELL_ID)).containsExactly("leukocyte");
    }

    @Test
    void shouldReturnEmptyListWhenNoChildrenExist() {
      when(efoTermMatcher.getChildIds(CELL_ID)).thenReturn(Set.of());

      assertThat(hierarchyService.getChildrenByEfoId(CELL_ID)).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForUnknownTerm() {
      assertThat(hierarchyService.getChildren("unknown")).isEmpty();
      verify(efoTermMatcher).getEFOId("unknown");
    }

    @Test
    void shouldReturnEmptyListForNullOrBlankInput() {
      assertThat(hierarchyService.getChildren(null)).isEmpty();
      assertThat(hierarchyService.getChildren("")).isEmpty();
      assertThat(hierarchyService.getChildren("   ")).isEmpty();

      assertThat(hierarchyService.getChildrenByEfoId(null)).isEmpty();
      assertThat(hierarchyService.getChildrenByEfoId("")).isEmpty();
      assertThat(hierarchyService.getChildrenByEfoId("   ")).isEmpty();
    }
  }

  @Nested
  @DisplayName("hasChildren() / hasChildrenByEfoId()")
  class HasChildrenTests {

    @Test
    void shouldReturnTrueWhenChildrenExist() {
      when(efoTermMatcher.getEFOId("cell")).thenReturn(CELL_ID);
      when(efoTermMatcher.getChildIds(CELL_ID)).thenReturn(Set.of(LEUKOCYTE_ID));
      when(efoTermMatcher.getTerm(LEUKOCYTE_ID)).thenReturn("leukocyte");

      assertThat(hierarchyService.hasChildren("cell")).isTrue();
      assertThat(hierarchyService.hasChildrenByEfoId(CELL_ID)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenChildrenDoNotExist() {
      when(efoTermMatcher.getEFOId("cell")).thenReturn(CELL_ID);
      when(efoTermMatcher.getChildIds(CELL_ID)).thenReturn(Set.of());

      assertThat(hierarchyService.hasChildren("cell")).isFalse();
      assertThat(hierarchyService.hasChildrenByEfoId(CELL_ID)).isFalse();
    }

    @Test
    void shouldReturnFalseForNullOrBlankInput() {
      assertThat(hierarchyService.hasChildren(null)).isFalse();
      assertThat(hierarchyService.hasChildren("")).isFalse();
      assertThat(hierarchyService.hasChildren("   ")).isFalse();

      assertThat(hierarchyService.hasChildrenByEfoId(null)).isFalse();
      assertThat(hierarchyService.hasChildrenByEfoId("")).isFalse();
      assertThat(hierarchyService.hasChildrenByEfoId("   ")).isFalse();
    }
  }
}
