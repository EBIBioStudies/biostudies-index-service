# BioStudies Index Service

Spring Boot service for Lucene indexing of BioStudies submissions with RabbitMQ real-time updates.

## Quick Start

1. Clone & build: `mvn clean package`
2. Run: `mvn spring-boot:run`
3. Search: `curl "localhost:8080/api/search?q=efo_term"`


```mermaid
graph LR
    A[Start] --> B[End]
```


[Full README →](../README.md)

## Documentation

- [Collections Registry](collections-registry.md) — technical overview of the registry format
    - [Collections](collections.md) — summary of all configured collections
- [Parsers](parsers.md) — parser reference
- [Analyzers](analyzers.md) — analyzer reference
- [API Reference](api.md)
- [K8s Deployment](deployment.md)
- [EFO Ontology](efo.md)
- [Lucene Indexes](lucene-indexes.md)