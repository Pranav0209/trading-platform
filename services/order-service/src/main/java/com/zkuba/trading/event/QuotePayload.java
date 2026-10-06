package com.zkuba.trading.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record QuotePayload(
        String symbol,
        BigDecimal price,
        BigDecimal bid,
        BigDecimal ask,
        String currency,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        OffsetDateTime asOf,
        String marketState
) {}
