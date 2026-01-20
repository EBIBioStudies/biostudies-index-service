package uk.ac.ebi.biostudies.index_service;

import java.util.List;
import uk.ac.ebi.biostudies.index_service.registry.dto.PropertyDescriptorDto;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionDescriptor;
import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;
import uk.ac.ebi.biostudies.index_service.registry.model.FieldType;
import uk.ac.ebi.biostudies.index_service.registry.model.PropertyDescriptor;

public class CollectionRegistryTestDataFactory {
  public static CollectionRegistry createValidRegistry() {
    PropertyDescriptorDto dto = new PropertyDescriptorDto();
    dto.setName("validField");
    dto.setFieldType(FieldType.FACET);
    dto.setJsonPaths(List.of("$.valid.path"));
    dto.setAnalyzer("AttributeFieldAnalyzer");

    CollectionDescriptor collection = new CollectionDescriptor("validCollection", List.of(dto));
    return new CollectionRegistry(List.of(collection));
  }

  public static CollectionRegistry createRegistryWithInvalidJsonPath() {
    PropertyDescriptorDto dto = new PropertyDescriptorDto();
    dto.setName("badJsonPath");
    dto.setFieldType(FieldType.FACET);
    dto.setJsonPaths(List.of("invalid[jsonpath"));

    CollectionDescriptor collection = new CollectionDescriptor("collection", List.of(dto));
    return new CollectionRegistry(List.of(collection));
  }

  public static CollectionRegistry createRegistryWithInvalidAnalyzer() {
    PropertyDescriptorDto dto = new PropertyDescriptorDto();
    dto.setName("badAnalyzer");
    dto.setFieldType(FieldType.FACET);
    dto.setAnalyzer("unknownAnalyzer");

    CollectionDescriptor collection = new CollectionDescriptor("collection", List.of(dto));
    return new CollectionRegistry(List.of(collection));
  }

  public static CollectionRegistry createEmptyRegistry() {
    return new CollectionRegistry(List.of());
  }
}
