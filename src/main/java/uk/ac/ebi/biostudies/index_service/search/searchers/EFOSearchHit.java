package uk.ac.ebi.biostudies.index_service.search.searchers;

import java.util.List;

/**
 * Represents a single EFO (Experimental Factor Ontology) search result from the EFO index.
 *
 * <p>This record encapsulates ontology term information including identifiers, labels, relationships,
 * and associated expansion terms used for query expansion in biological searches.
 *
 * <p>All instances are immutable.
 *
 * @param id doc ID
 * @param efoID the EFO identifier (e.g., "EFO:0000311")
 * @param term the primary term label (e.g., "cancer")
 * @param child child term in the ontology hierarchy, if applicable
 * @param altTerm alternative term label, if available
 * @param synonyms list of synonym terms for query expansion (may be null or empty)
 * @param efoTerms list of related EFO term identifiers for query expansion (may be null or empty)
 */
public record EFOSearchHit(
    String id,
    String efoID,
    String term,
    String child,
    String altTerm,
    List<String> synonyms,
    List<String> efoTerms) {}
