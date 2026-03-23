# File Index

The file index stores Lucene documents for files associated with BioStudies submissions.

It is populated from file metadata and normalized by the file document factory. The index contains
both core file fields and dynamic attribute fields discovered during indexing.

## Main fields

| Field Name         | Type     | Stored? | Indexed | Description                                     | Notes                                                                                        |
|--------------------|----------|---------|---------|-------------------------------------------------|----------------------------------------------------------------------------------------------|
| `id`               | `string` | Yes     | Yes     | Unique document identifier for the file entry.  | Derived from file identity and position.                                                     |
| `file_position`    | `long`   | Yes     | No      | Position of the file within the submission.     | Used for ordering.                                                                           |
| `file_path`        | `string` | Yes     | Yes     | Full path of the file.                          | Uses `filePath` when available, otherwise falls back to `relPath`.                           |
| `file_name`        | `string` | Yes     | Yes     | File name.                                      | Derived from `fileName` or from the last path segment. Indexed in normalized and exact form. |
| `file_size`        | `long`   | Yes     | Yes     | File size, usually in bytes.                    | Defaults to `0` when missing.                                                                |
| `file_section`     | `string` | Yes     | Yes     | Section accession the file belongs to.          | Present for non-study root sections only.                                                    |
| `file_type`        | `string` | Yes     | Yes     | File entry type.                                | Standard value is `file`.                                                                    |
| `file_isDirectory` | `string` | Yes     | Yes     | Indicates whether the entry is a directory.     | Stored as `true` or `false`.                                                                 |
| `file_owner`       | `string` | Yes     | Yes     | Accession of the submission that owns the file. | Links the file document back to the parent submission.                                       |

## Dynamic fields

The file index also stores dynamic attribute fields discovered in file metadata.

These fields:

- are indexed and stored
- are normalized to lowercase for indexed values
- are added only when the attribute name/value pair is valid and not duplicated

Examples include file-level metadata such as description, format, or other submission-specific
attributes.

## Notes

- The file index is built from file-level JSON metadata.
- Core fields are always created by the file document factory.
- Dynamic attributes extend the index per submission, depending on the file content.
- Some attributes may be excluded for specific submission types to avoid conflicting metadata.