package com.zkuba.trading.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends GlobalException {
    public UnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, "AUTH-401", "Unauthorised");
    }

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
