package com.zkuba.trading.domain.exception;

public class ForbiddenException extends GlobalException {
    public ForbiddenException() {
        super("AUTH-403", "Access denied");
    }

    protected ForbiddenException(String code, String message) {
        super(code, message);
    }
}
