package com.zkuba.trading.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends GlobalException {
    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "ACC-403", "Access denied");
    }

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
