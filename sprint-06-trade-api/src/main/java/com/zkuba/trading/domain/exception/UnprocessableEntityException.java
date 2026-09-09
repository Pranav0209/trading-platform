package com.zkuba.trading.domain.exception;

public abstract class UnprocessableEntityException extends GlobalException {
    protected UnprocessableEntityException(String code, String message) {
        super(code, message);
    }
}