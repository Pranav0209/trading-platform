package com.zkuba.trading.dto.response;

import java.math.BigDecimal;

public record OrderHistoryEntry(
        String orderId,
        Long accountId,
        String symbol,
        String side,
        int quantity,
        BigDecimal price,
        BigDecimal executedPrice,
        String status,
        String rejectReason,
        String idempotencyKey,
        String createdOn
) {}
