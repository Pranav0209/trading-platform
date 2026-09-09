package com.zkuba.trading.domain.exception;

public class UnauthorizedException extends GlobalException {
    public UnauthorizedException() {
        super("AUTH-401", "Invalid credentials");
    }
}
