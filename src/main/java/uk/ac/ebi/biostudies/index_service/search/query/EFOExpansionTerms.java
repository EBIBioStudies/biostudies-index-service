package uk.ac.ebi.biostudies.index_service.search.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Contains EFO and synonym expansion terms for a query. */
@Getter
@Setter
public class EFOExpansionTerms {

  /** The original term being expanded */
  String term;

  /** EFO ontology terms */
  Set<String> efo = new HashSet<>();

  /** Synonym terms */
  Set<String> synonyms = new HashSet<>();
}
