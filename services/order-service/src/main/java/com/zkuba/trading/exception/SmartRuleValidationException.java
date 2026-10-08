package com.zkuba.trading.exception;

public class SmartRuleValidationException extends UnprocessableEntityException {
    public SmartRuleValidationException(String message) {
        super("SRL-422", message);
    }
}