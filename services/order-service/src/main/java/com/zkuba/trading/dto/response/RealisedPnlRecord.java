package com.zkuba.trading.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Single realised P&L record returned in the history API.
 */
public record RealisedPnlRecord(
        Long pnlId,
        UUID orderId,
        String symbol,
        Integer quantity,
        BigDecimal sellPrice,
        BigDecimal averageCost,
        BigDecimal realisedPnl,
        OffsetDateTime realisedAt
) {}
