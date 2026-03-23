# Lucene Indexes

This page describes the Lucene indexes used by the BioStudies Index Service.

The index locations are controlled by related configuration properties, including:

- `index.base-dir` — Base directory path where all Lucene indexes are stored on the filesystem.

## Indexes

| Name               | Description                                                                                                             | Docs                                                      |
|--------------------|-------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| `submission_index` | Main index for BioStudies submissions. It stores submission-level metadata and supports search across indexed fields.   | [submission_index.md](lucene-indexes/submission_index.md) |
| `file_index`       | Index for submission-related file documents and file metadata. It supports file-specific search and lookup.             | [file_index.md](lucene-indexes/file_index.md)             |
| `efo_index`        | Index for EFO ontology terms and related expansion data. It supports ontology search, autocomplete, and term expansion. | [efo_index.md](lucene-indexes/efo_index.md)               |

## Notes

- The actual filesystem location of each index is derived from `index.base-dir`.
- These indexes work together as part of the search and indexing pipeline.
- The submission index is the primary search index, while the file and EFO indexes provide
  supporting search capabilities.