package com.zkuba.trading.domain.exception;

public class OrderConflictException extends ConflictException {
    public OrderConflictException(String message) {
        super("ORD-409", message);
    }

    public OrderConflictException() {
        super("ORD-409", "Order processing conflict due to concurrent update");
    }
}
