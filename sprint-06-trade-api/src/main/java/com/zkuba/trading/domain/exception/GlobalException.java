package com.zkuba.trading.domain.exception;

public abstract class GlobalException extends RuntimeException {

    private final String code;

    protected GlobalException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
