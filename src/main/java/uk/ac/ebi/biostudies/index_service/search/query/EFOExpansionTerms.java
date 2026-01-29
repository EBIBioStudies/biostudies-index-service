package uk.ac.ebi.biostudies.index_service.search.query;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Contains EFO and synonym expansion terms for a query. */
@Getter
@Setter
public class EFOExpansionTerms {

  /** The original term being expanded */
  String term;

  /** EFO ontology terms */
  List<String> efo = new ArrayList<>();

  /** Synonym terms */
  List<String> synonyms = new ArrayList<>();
}
