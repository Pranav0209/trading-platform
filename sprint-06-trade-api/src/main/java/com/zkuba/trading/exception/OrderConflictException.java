package com.zkuba.trading.exception;

public class OrderConflictException extends com.zkuba.trading.domain.exception.OrderConflictException {
    public OrderConflictException(String message) {
        super(message);
    }

    public OrderConflictException() {
        super();
    }
}
