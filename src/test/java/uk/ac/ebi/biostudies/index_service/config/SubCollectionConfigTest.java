package uk.ac.ebi.biostudies.index_service.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("SubCollectionConfig Tests")
class SubCollectionConfigTest {

  @Autowired private SubCollectionConfig config;

  @Test
  @DisplayName("Debug: Show what's in the maps")
  void debugMaps() {
    System.out.println("Parent to Children Map:");
    config
        .getParentToChildrenMap()
        .forEach(
            (parent, children) -> {
              System.out.println("  " + parent + " -> " + children);
            });

    System.out.println("\nChild to Parent Map:");
    config
        .getChildToParentMap()
        .forEach(
            (child, parent) -> {
              System.out.println("  " + child + " -> " + parent);
            });

    System.out.println("\nHierarchy for 'BioImages':");
    List<String> hierarchy = config.getHierarchy("BioImages");
    System.out.println("  " + hierarchy);

    System.out.println("\nHas children 'BioImages': " + config.hasChildren("BioImages"));
    System.out.println("Children of 'BioImages': " + config.getChildren("BioImages"));
  }

  @Nested
  @DisplayName("Configuration Loading")
  class ConfigurationLoading {

    @Test
    @DisplayName("Should load subcollections from YAML")
    void shouldLoadSubcollectionsFromYaml() {
      assertNotNull(config.getParentToChildrenMap());
      assertNotNull(config.getChildToParentMap());
    }

    @Test
    @DisplayName("Should have BioImages hierarchy configured")
    void shouldHaveBioImagesHierarchy() {
      assertTrue(config.hasChildren("BioImages"));
      assertEquals(2, config.getChildren("BioImages").size());
    }
  }

  @Nested
  @DisplayName("Parent to Children Mapping")
  class ParentToChildrenMapping {

    @Test
    @DisplayName("Should return children for BioImages parent")
    void shouldReturnChildrenForBioImages() {
      List<String> children = config.getChildren("BioImages");

      assertNotNull(children);
      assertEquals(2, children.size());
      assertTrue(children.contains("JCB"));
      assertTrue(children.contains("BioImages-EMPIAR"));
    }

    @Test
    @DisplayName("Should return empty list for collection with no children")
    void shouldReturnEmptyListForNoChildren() {
      List<String> children = config.getChildren("NonExistentCollection");

      assertNotNull(children);
      assertTrue(children.isEmpty());
    }

    @Test
    @DisplayName("Should return immutable list")
    void shouldReturnImmutableList() {
      List<String> children = config.getChildren("BioImages");

      assertThrows(UnsupportedOperationException.class, () -> children.add("NewChild"));
    }

    @Test
    @DisplayName("Should correctly identify parent collections")
    void shouldIdentifyParentCollections() {
      assertTrue(config.hasChildren("BioImages"));
      assertFalse(config.hasChildren("JCB"));
      assertFalse(config.hasChildren("BioImages-EMPIAR"));
      assertFalse(config.hasChildren("RandomCollection"));
    }
  }

  @Nested
  @DisplayName("Child to Parent Mapping")
  class ChildToParentMapping {

    @Test
    @DisplayName("Should return parent for JCB child")
    void shouldReturnParentForJCB() {
      String parent = config.getParent("JCB");

      assertEquals("BioImages", parent);
    }

    @Test
    @DisplayName("Should return parent for BioImages-EMPIAR child")
    void shouldReturnParentForEMPIAR() {
      String parent = config.getParent("BioImages-EMPIAR");

      assertEquals("BioImages", parent);
    }

    @Test
    @DisplayName("Should return null for collection with no parent")
    void shouldReturnNullForNoParent() {
      String parent = config.getParent("BioImages");

      assertNull(parent);
    }

    @Test
    @DisplayName("Should return null for non-existent collection")
    void shouldReturnNullForNonExistent() {
      String parent = config.getParent("NonExistentCollection");

      assertNull(parent);
    }
  }

  @Nested
  @DisplayName("Hierarchy Retrieval")
  class HierarchyRetrieval {

    @Test
    @DisplayName("Should get full hierarchy for parent collection")
    void shouldGetFullHierarchyForParent() {
      List<String> hierarchy = config.getHierarchy("BioImages");

      assertEquals(3, hierarchy.size());
      assertTrue(hierarchy.contains("BioImages"));
      assertTrue(hierarchy.contains("JCB"));
      assertTrue(hierarchy.contains("BioImages-EMPIAR"));
    }

    @Test
    @DisplayName("Should get full hierarchy when querying by child")
    void shouldGetFullHierarchyForChild() {
      List<String> hierarchy = config.getHierarchy("JCB");

      assertEquals(3, hierarchy.size());
      assertTrue(hierarchy.contains("BioImages"));
      assertTrue(hierarchy.contains("JCB"));
      assertTrue(hierarchy.contains("BioImages-EMPIAR"));
    }

    @Test
    @DisplayName("Should return only itself for standalone collection")
    void shouldReturnOnlyItselfForStandalone() {
      List<String> hierarchy = config.getHierarchy("StandaloneCollection");

      assertEquals(1, hierarchy.size());
      assertEquals("StandaloneCollection", hierarchy.get(0));
    }

    @Test
    @DisplayName("Should handle case sensitivity correctly")
    void shouldHandleCaseSensitivity() {
      // Assuming collection names are case-sensitive
      List<String> hierarchy = config.getHierarchy("bioimages");

      assertEquals(1, hierarchy.size()); // Only itself, no match to "BioImages"
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Should handle null parent query gracefully")
    void shouldHandleNullParentQuery() {
      String parent = config.getParent(null);

      assertNull(parent);
    }

    @Test
    @DisplayName("Should handle null children query gracefully")
    void shouldHandleNullChildrenQuery() {
      List<String> children = config.getChildren(null);

      assertNotNull(children);
      assertTrue(children.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty string queries")
    void shouldHandleEmptyStringQueries() {
      assertNull(config.getParent(""));
      assertTrue(config.getChildren("").isEmpty());
      assertFalse(config.hasChildren(""));
    }
  }

  @Nested
  @DisplayName("Cache Immutability")
  class CacheImmutability {

    @Test
    @DisplayName("Should not allow modification of parent map")
    void shouldNotAllowParentMapModification() {
      assertThrows(
          UnsupportedOperationException.class,
          () -> config.getParentToChildrenMap().put("Test", List.of("Child")));
    }

    @Test
    @DisplayName("Should not allow modification of child map")
    void shouldNotAllowChildMapModification() {
      assertThrows(
          UnsupportedOperationException.class,
          () -> config.getChildToParentMap().put("Test", "Parent"));
    }
  }
}
