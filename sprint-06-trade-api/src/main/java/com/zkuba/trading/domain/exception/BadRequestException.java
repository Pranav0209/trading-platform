package com.zkuba.trading.domain.exception;

public abstract class BadRequestException extends GlobalException {
    protected BadRequestException(String code, String message) {
        super(code, message);
    }
}