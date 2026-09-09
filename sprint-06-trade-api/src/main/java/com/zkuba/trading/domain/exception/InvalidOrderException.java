package com.zkuba.trading.domain.exception;

public class InvalidOrderException extends BadRequestException {
    public InvalidOrderException(String message) {
        super("ORD-400", message);
    }
}
