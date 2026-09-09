package com.zkuba.trading.domain.exception;

public abstract class ConflictException extends GlobalException {
    protected ConflictException(String code, String message) {
        super(code, message);
    }
}