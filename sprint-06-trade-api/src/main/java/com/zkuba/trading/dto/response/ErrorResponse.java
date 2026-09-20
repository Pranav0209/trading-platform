package com.zkuba.trading.dto.response;

import java.io.Serializable;

public record ErrorResponse(
        int status,
        String error,
        String message) implements Serializable {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message);
    }
}
