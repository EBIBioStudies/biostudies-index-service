# Collections Registry

The collections registry defines how BioStudies submissions are mapped into indexed fields for
search, faceting, retrieval, and sorting.

It is stored as JSON and loaded at application startup. Each collection in the registry contains a
set of property definitions that describe:

- the field name,
- the display title,
- the field type,
- optional JSONPath selectors,
- optional parsers,
- optional analyzers,
- and indexing behavior flags such as retrieval, sorting, and multi-value handling.

To see the full registry, see
[collections-registry.json](https://raw.githubusercontent.com/EBIBioStudies/biostudies-index-service/main/src/main/resources/schema/collections-registry.json)

## Purpose

The registry is the central schema configuration for the indexing pipeline. It allows the system to
support multiple collections with different metadata models while still sharing common public
fields.

## Registry structure

At the top level, the registry is a list of collections.

Each collection contains:

- `collectionName` — the logical name of the collection
- `properties` — the fields defined for that collection

## Property definition

Each property may define the following attributes:

| Attribute      | Description                                          |
|----------------|------------------------------------------------------|
| `name`         | Unique field name used in the index                  |
| `title`        | Human-readable label for the field                   |
| `fieldType`    | Field type used by the indexing pipeline             |
| `jsonPaths`    | JSONPath expressions used to extract values          |
| `parser`       | Parser used to compute or normalize the field value  |
| `analyzer`     | Analyzer used for tokenization and indexing          |
| `retrieved`    | Whether the field is stored for retrieval            |
| `sortable`     | Whether the field can be sorted                      |
| `multiValued`  | Whether the field can contain multiple values        |
| `expanded`     | Whether the field should be expanded during indexing |
| `private`      | Whether the field is hidden from public use          |
| `defaultValue` | Fallback value used when no data is found            |
| `facetType`    | Facet-specific type, such as boolean                 |
| `naVisible`    | Whether `NA` values should be visible                |
| `toLowerCase`  | Whether values should be normalized to lowercase     |
| `match`        | Pattern used to extract or normalize values          |

## Supported collections

The registry currently includes the following collections:

- `public`
- `idr`
- `hecatos`
- `arrayexpress`
- `biomodels`
- `europepmc`
- `eu-toxrisk`
- `rh3r`
- `cancermodelsorg`

## Public collection

The `public` collection contains shared fields that apply broadly across submissions. It includes
core fields such as:

- accession
- type
- title
- author
- content
- links
- files
- release date and time fields
- collection metadata
- access-related fields

These fields form the common indexing layer used across collections.

## Collection-specific fields

Collection-specific entries define additional metadata for specialized datasets.

Examples include:

- study and assay metadata for `arrayexpress`
- model metadata for `biomodels`
- toxicology metadata for `eu-toxrisk`
- cancer model metadata for `cancermodelsorg`

## Parsers and analyzers

Some fields use custom parsers to transform raw JSON into indexed values.

Examples:

- `AccessParser` for access metadata
- `ContentParser` for full content assembly
- `JPathListParser` for list-like JSONPath extraction
- `NodeCountingParser` for count-based fields
- `ReleaseDateParser`, `ReleaseTimeParser`, `ReleaseYearParser`
- `CreationTimeParser`, `ModificationTimeParser`, `ModificationYearParser`
- `ViewCountParser`
- `FileTypeParser`
- `TypeParser`
- `EUToxRiskDataTypeParser`
- `NullParser`

Analyzer selection also varies by field:

- `AttributeFieldAnalyzer`
- `AccessFieldAnalyzer`
- `ExperimentTextAnalyzer`

## Validation rules

The registry is expected to follow a few important rules:

- collection names should be unique
- property names must be unique across the full registry
- fields must be well-formed for their declared type
- parser and analyzer names must match supported implementations
