package com.zkuba.executor.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPlacedPayload(
    UUID orderId,
    Long accountId,
    String symbol,
    String side,
    Integer quantity,
    BigDecimal price,
    String idempotencyKey,
    Instant createdOn
) {
}
