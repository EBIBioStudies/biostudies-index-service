package uk.ac.ebi.biostudies.index_service.rest;

import java.util.List;

public record RestResponse<T>(boolean success, String message, T data, List<ApiError> errors) {

  public static <T> RestResponse<T> success(String msg, T data) {
    return new RestResponse<>(true, msg, data, List.of());
  }

  public static RestResponse<Void> errorVoid(String msg, List<ApiError> errors) {
    return new RestResponse<>(false, msg, null, errors);
  }

  public static RestResponse<Void> error(String msg, List<ApiError> errors) {
    return new RestResponse<>(false, msg, null, errors);
  }

}
