package uk.ac.ebi.biostudies.index_service.search.files;

/** UI-agnostic column filter/sort spec */
public record ColumnSpec(String name, String value, String dir) {}
