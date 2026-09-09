package com.zkuba.trading.dto.response;

public record AccountResponse(
        Long id,
        String accountId,
        String holderName,
        java.math.BigDecimal cashBalance,
        String status,
        Integer version,
        String lastUpdated) {}
