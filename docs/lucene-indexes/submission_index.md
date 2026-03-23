# Submission Index

The submission index is the main Lucene index used for BioStudies submission search and retrieval.

It stores the indexed representation of submission-level metadata from the `public` collection in
the registry + the collection-specific ones, along with a few filesystem-related fields used by the
indexing pipeline.

## Main fields

| Field Name     | Type                 | Stored | Indexed | Description                                                             | Notes                                                                  |
|----------------|----------------------|--------|---------|-------------------------------------------------------------------------|------------------------------------------------------------------------|
| `access`       | `tokenized_string`   | No     | Yes     | Access-related metadata used for filtering and access control.          | Uses a custom parser and analyzer. Values are normalized to lowercase. |
| `accession`    | `tokenized_string`   | Yes    | Yes     | Submission accession identifier.                                        | Retrieved as a core identifier field.                                  |
| `type`         | `untokenized_string` | Yes    | Yes     | Submission type.                                                        | Stored for retrieval and used in filtering.                            |
| `title`        | `tokenized_string`   | Yes    | Yes     | Submission title.                                                       | Sorted and analyzed as full text.                                      |
| `author`       | `tokenized_string`   | Yes    | Yes     | Author names extracted from the submission.                             | Multi-value field parsed from structured content.                      |
| `content`      | `tokenized_string`   | No     | Yes     | Full-text content assembled from submission sections, files, and links. | Main full-text search field.                                           |
| `links`        | `long`               | Yes    | Yes     | Count of link entries associated with the submission.                   | Numeric field used for sorting and retrieval.                          |
| `files`        | `long`               | Yes    | Yes     | Count of file entries associated with the submission.                   | Numeric field used for sorting and retrieval.                          |
| `release_date` | `untokenized_string` | Yes    | Yes     | Submission release date.                                                | Stored as a retrievable field.                                         |
| `ctime`        | `long`               | No     | Yes     | Submission creation time.                                               | Numeric timestamp field.                                               |
| `mtime`        | `long`               | No     | Yes     | Submission modification time.                                           | Numeric timestamp field.                                               |
| `relPath`      | `untokenized_string` | No     | Yes     | Relative path of the indexed submission source.                         | Used as a filesystem-oriented metadata field.                          |
| `storageMode`  | `untokenized_string` | No     | Yes     | Storage mode for the submission.                                        | Defaults to `NFS` when not provided.                                   |

## Notes

- The fields above are driven by the `public` collection definition in the collections registry, as
  well as the collection-specific fields defined in the collection-specific index mappings.
- Some fields are derived by parsers rather than taken directly from a single JSON path.
- `content` is the main searchable text field.
- `access` uses access-oriented parsing and analyzer behavior.
- Numeric fields such as `links`, `files`, `ctime`, and `mtime` are used for sorting and metadata
  handling rather than free-text search.