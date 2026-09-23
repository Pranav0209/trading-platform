package com.zkuba.executor.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TradeEventPayload(
        UUID orderId,
        Long accountId,
        String symbol,
        String side,
        int quantity,
        BigDecimal price,
        BigDecimal executedPrice,
        String status,
        String reason,
        BigDecimal cashDelta,
        Integer positionQuantityAfter,
        BigDecimal averageCostAfter,
        OffsetDateTime executedOn
) {
}
