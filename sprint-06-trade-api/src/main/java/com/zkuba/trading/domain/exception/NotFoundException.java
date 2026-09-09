package com.zkuba.trading.domain.exception;

public abstract class NotFoundException extends GlobalException {
    protected NotFoundException(String code, String message) {
        super(code, message);
    }
}