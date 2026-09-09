package com.zkuba.trading.exception;

import org.springframework.http.HttpStatus;

public abstract class UnprocessableEntityException extends GlobalException {
    protected UnprocessableEntityException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
