package com.zkuba.trading.exception;

public class InvalidOrderException extends UnprocessableEntityException {
    public InvalidOrderException(String message) {
        super("VAL-422", message);
    }
}
