package com.zkuba.trading.dto.response;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;


public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldError> fieldErrors
) implements Serializable {

    public record FieldError(String field, String message) {}


    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message, String path,
                                                List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }
}
