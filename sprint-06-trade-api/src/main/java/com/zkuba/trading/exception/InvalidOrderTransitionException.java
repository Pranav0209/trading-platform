package com.zkuba.trading.exception;

import com.zkuba.trading.domain.enums.OrderStatus;
import java.util.UUID;

public class InvalidOrderTransitionException extends ConflictException {
    public InvalidOrderTransitionException(UUID orderId, OrderStatus currentStatus, String operation) {
        super("ORD-409", "Order " + orderId + " cannot be " + operation + ": already " + currentStatus);
    }
}
