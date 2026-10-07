package com.zkuba.trading.exception;

public class AlertValidationException extends UnprocessableEntityException {
    public AlertValidationException(String message) {
        super("ALT-422", message);
    }
}
