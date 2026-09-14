package com.zkuba.trading.exception;

import org.springframework.http.HttpStatus;

public abstract class ConflictException extends GlobalException {
    protected ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
