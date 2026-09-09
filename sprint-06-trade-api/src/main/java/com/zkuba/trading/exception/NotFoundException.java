package com.zkuba.trading.exception;

import org.springframework.http.HttpStatus;

public abstract class NotFoundException extends GlobalException {
    protected NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
