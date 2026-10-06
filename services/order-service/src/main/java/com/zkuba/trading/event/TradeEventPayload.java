package com.zkuba.trading.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TradeEventPayload(
        UUID orderId,
        Long accountId,
        String symbol,
        String side,
        Integer quantity,
        BigDecimal limitPrice,
        BigDecimal executedPrice,
        String status,
        String rejectReason,
        BigDecimal cashDelta,
        Integer quantityAfter,
        BigDecimal averageCostAfter,
        OffsetDateTime timestamp
) {}
