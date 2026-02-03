package uk.ac.ebi.biostudies.index_service.search;

import java.util.List;
import java.util.Map;
import java.util.Set;
import uk.ac.ebi.biostudies.index_service.search.searchers.SubmissionSearchHit;

/**
 * DTO representing paginated search results with facets and query expansion metadata.
 *
 * @param page current page number (1-based)
 * @param pageSize number of results per page
 * @param totalHits total number of matching documents
 * @param isTotalHitsExact whether totalHits is exact or an estimate
 * @param sortBy field used for sorting
 * @param sortOrder sort direction (ascending/descending)
 * @param suggestion list of query suggestions
 * @param expandedEfoTerms list of expanded EFO (Experimental Factor Ontology) terms
 * @param expandedSynonyms list of expanded synonym terms
 * @param query the processed query string
 * @param facets map of facet names to their selected values
 * @param hits list of search result documents
 * @param tooManyExpansionTerms flag indicating if too many expansion terms were encountered
 */
public record SearchResponseDTO(
    int page,
    int pageSize,
    long totalHits,
    boolean isTotalHitsExact,
    String sortBy,
    String sortOrder,
    List<String> suggestion,
    Set<String> expandedEfoTerms,
    Set<String> expandedSynonyms,
    String query,
    Map<String, List<String>> facets,
    List<SubmissionSearchHit> hits,
    boolean tooManyExpansionTerms) {}
