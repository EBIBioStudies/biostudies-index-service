package uk.ac.ebi.biostudies.index_service.search.engine;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Represents a single search result (from submission index) document returned from a search query.
 *
 * <p>This record encapsulates the core metadata and content of a study or submission that matches
 * the search criteria. All instances are immutable and contain a fixed set of fields that
 * correspond to the indexed document structure.
 *
 * @param accession unique identifier for the study (e.g., "S-EPMC6171881")
 * @param type the type of the document (e.g., "study")
 * @param title the full title of the study
 * @param author space-separated list of author names
 * @param links number of links associated with this study
 * @param files number of files associated with this study
 * @param releaseDate the date when the study was released or published
 * @param views the number of times this study has been viewed
 * @param isPublic whether the study is publicly accessible
 * @param content text snippet or excerpt from the study content, typically used for search context
 */
// Configure Jackson to serialize LocalDate as "yyyy-MM-dd"
@JsonFormat(pattern = "yyyy-MM-dd")
public record SubmissionSearchHit(
    String accession,
    String type,
    String title,
    String author,
    int links,
    int files,
    @JsonProperty("release_date") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate releaseDate,
    int views,
    boolean isPublic,
    String content) {}
