package com.zkuba.trading.exception;

public class OrderConflictException extends ConflictException {
    public OrderConflictException(String message) {
        super("ORD-409", message);
    }
}
