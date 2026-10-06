package com.zkuba.trading.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for invalid or out-of-range query parameters.
 * Corresponds to HTTP 400 Bad Request with error code VAL-422.
 */
public class InvalidParametersException extends GlobalException {
    public InvalidParametersException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
