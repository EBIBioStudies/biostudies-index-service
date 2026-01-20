package uk.ac.ebi.biostudies.index_service.fileaccess;

/**
 * Immutable description of a file request coming from the client/UI.
 *
 * @param accession Study accession (e.g. "S-BSST1432").
 * @param requestedPath Normalized path requested by the UI (decoded).
 * @param rawRequestedPath Original requested path before decoding, if applicable.
 * @param fileName Logical file name (usually the last path segment).
 * @param collection Logical collection name (e.g. "arrayexpress").
 * @param studyRelativePath Path relative to the study root in storage.
 * @param publicStudy Whether the study is publicly accessible.
 * @param thumbnailRequested Whether a thumbnail representation was requested.
 * @param hasSecretKey Whether a secretKey has been provided.
 * @param secretKey Secret key used to access private studies (formerly secKey).
 */
public record FileRequest(
    String accession,
    String requestedPath,
    String rawRequestedPath,
    String fileName,
    String collection,
    String studyRelativePath,
    boolean publicStudy,
    boolean thumbnailRequested,
    boolean hasSecretKey,
    String secretKey,
    StorageMode storageMode) {}
