# EFO Index

The EFO index stores Lucene documents for Experimental Factor Ontology terms used by autocomplete
and ontology browsing features.

It supports term search, synonym expansion, and hierarchy navigation. The index contains primary
ontology terms, hierarchy relationships, and alternative term entries used for query expansion.

## Main fields

| Field Name | Type     | Stored? | Indexed | Description                                 | Notes                                                     |
|------------|----------|---------|---------|---------------------------------------------|-----------------------------------------------------------|
| `id`       | `string` | Yes     | Yes     | Unique identifier for the ontology node.    | Used as the internal document identifier.                 |
| `term`     | `string` | Yes     | Yes     | Primary ontology term label.                | Main searchable term in the ontology index.               |
| `child`    | `string` | Yes     | Yes     | Child node identifiers.                     | Used for hierarchy expansion and navigation.              |
| `alt_term` | `string` | Yes     | Yes     | Alternative term or synonym.                | Stored as separate searchable entries for synonym lookup. |
| `efo_id`   | `string` | Yes     | Yes     | EFO accession or URI for the ontology term. | Present for primary ontology nodes.                       |
| `parent`   | `string` | Yes     | Yes     | Parent node identifiers.                    | Used to represent the ontology hierarchy.                 |
| `qe.term`  | `string` | Yes     | Yes     | Query-expansion term field.                 | Supports autocomplete expansion behavior.                 |
| `qe.efo`   | `string` | Yes     | Yes     | Query-expansion EFO field.                  | Supports ontology-based query expansion workflows.        |

## Notes

- The EFO index is built from ontology data rather than submission metadata.
- Primary term documents store hierarchy relationships such as parents and children.
- Alternative terms are indexed as separate documents to improve synonym matching.
- The index is used by autocomplete and tree-navigation features.
- Indexed values are normalized to support consistent search behavior.