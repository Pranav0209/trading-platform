package com.zkuba.trading.dto.response;

public record OrderResponse(
        String orderId,
        String status,
        String symbol,
        String side,
        String orderType,
        int quantity,
        java.math.BigDecimal limitPrice,
        java.math.BigDecimal executedPrice,
        String message) {}
