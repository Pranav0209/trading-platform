package com.zkuba.trading.domain.exception;

public abstract class GlobalExceptionHandler extends RuntimeException {

    private final String code;

    protected GlobalExceptionHandler(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
