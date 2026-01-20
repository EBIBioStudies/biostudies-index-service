package uk.ac.ebi.biostudies.index_service.registry.service;

import uk.ac.ebi.biostudies.index_service.registry.model.CollectionRegistry;

public interface CollectionRegistryValidator {
  /**
   * Validates the entire collection registry.
   *
   * @param registry the registry to validate (not null)
   * @throws IllegalStateException if any validation errors are found
   */
  void validate(CollectionRegistry registry);
}
