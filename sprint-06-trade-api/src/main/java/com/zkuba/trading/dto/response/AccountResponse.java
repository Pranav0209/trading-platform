package com.zkuba.trading.dto.response;

public record AccountResponse(
        Long id,
        String accountNumber,
        Long userId,
        java.math.BigDecimal cashBalance,
        String currency,
        String status) {}
