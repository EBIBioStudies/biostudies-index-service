package uk.ac.ebi.biostudies.index_service.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.biostudies.index_service.autocomplete.AutoCompleteService;

/**
 * REST controller providing autocomplete functionality for keyword searches based on EFO
 * (Experimental Factor Ontology) terms.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/autocomplete")
public class AutoCompleteController {
  private final AutoCompleteService autoCompleteService;

  public AutoCompleteController(AutoCompleteService autoCompleteService) {
    this.autoCompleteService = autoCompleteService;
  }

  /**
   * Retrieves autocomplete suggestions for EFO ontology terms.
   *
   * <p>Returns plain text with one suggestion per line in the format: {@code term|o|uri} where:
   *
   * <ul>
   *   <li>'o' indicates an ontology term
   *   <li>'uri' is the ontology identifier, included only if the term has children (is expandable)
   * </ul>
   *
   * <p>Simple queries are automatically enhanced with a wildcard suffix for prefix matching.
   * Queries with quotes, boolean operators (AND, OR), or wildcards (*) are not modified.
   *
   * @param query the search term to autocomplete
   * @param limit maximum number of suggestions to return (defaults to 50, capped at 200)
   * @return pipe-delimited plain text with suggestions, or empty string if query is empty
   */
  @RequestMapping(
      value = "/keywords",
      produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8",
      method = RequestMethod.GET)
  public String getKeywords(
      @RequestParam(value = "query", defaultValue = "") String query,
      @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
    if (query == null || query.isEmpty()) return "";
    return autoCompleteService.getKeywords(query, limit);
  }

  /**
   * Retrieves direct children of a given EFO term for tree navigation.
   *
   * <p>Returns plain text with one child term per line in the format: {@code term|o|uri} where:
   *
   * <ul>
   *   <li>'o' indicates an ontology term
   *   <li>'uri' is the ontology identifier of the child term, included only if the child has its
   *       own children (is expandable in the UI)
   * </ul>
   *
   * <p>The endpoint is used by the UI to expand EFO nodes in a hierarchical tree. Results are
   * sorted alphabetically and are not filtered by submission index presence, so the full ontology
   * structure remains navigable.
   *
   * @param efoId the EFO term identifier (URI) whose children should be returned
   * @return pipe-delimited plain text with child terms, or empty string if {@code efoid} is empty
   */
  @RequestMapping(
      value = "/efotree",
      produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8",
      method = RequestMethod.GET)
  public String getEfoTree(@RequestParam(value = "efoid", defaultValue = "") String efoId) {
    if (efoId == null || efoId.isEmpty()) return "";
    return autoCompleteService.getEfoTree(efoId);
  }
}
