package com.zkuba.trading.exception;

import org.springframework.http.HttpStatus;

public abstract class BadRequestException extends GlobalException {
    protected BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
