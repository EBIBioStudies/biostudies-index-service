package uk.ac.ebi.biostudies.index_service.model;

/**
 * JSON property names for file objects in the extended submission model.
 */
public enum ExtendedFileProperty {
  FILE_NAME("fileName"),
  FILE_PATH("filePath"),
  REL_PATH("relPath"),
  FULL_PATH("fullPath"),
  MD5("md5"),
  SIZE("size"),
  TYPE("type"),
  ATTRIBUTES("attributes"),
  IS_DIRECTORY("isDirectory"),
  EXT_TYPE("extType");

  private final String name;

  ExtendedFileProperty(String jsonName) {
    this.name = jsonName;
  }

  /**
   * Returns the JSON property name as it appears in the extended endpoint response.
   */
  public String getName() {
    return name;
  }
}
