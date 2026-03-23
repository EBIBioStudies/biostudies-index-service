<llm-snippet-file>docs/parsers.md</llm-snippet-file>
# Parsers

| Parser name | Description |
| --- | --- |
| `SimpleAttributeParser` | Default parser that reads a value from a matching attribute. |
| `NodeCountingParser` | Counts and analyzes nodes in a document. |
| `EUToxRiskDataTypeParser` | Handles EUToxRisk data type processing. |
| `AccessParser` | Extracts access-related metadata for indexing and filtering. |
| `TypeParser` | Extracts and interprets type-related metadata. |
| `JPathListParser` | Uses JPath expressions to extract lists from structured data. |
| `FileTypeParser` | Determines file format and type information. |
| `ContentParser` | Processes the core content of a document. |
| `CreationTimeParser` | Extracts creation time as milliseconds since epoch. |
| `ModificationTimeParser` | Extracts modification time as milliseconds since epoch. |
| `ModificationYearParser` | Extracts modification year. |
| `ReleaseTimeParser` | Extracts release time as milliseconds since epoch. |
| `ReleaseDateParser` | Extracts release date. |
| `ReleaseYearParser` | Extracts release year. |
| `NullParser` | No-operation placeholder parser. |
| `ViewCountParser` | Extracts document view count metadata. |
| `TitleParser` | Extracts and processes document titles. |