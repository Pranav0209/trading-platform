package com.zkuba.executor.client;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record QuoteResponse(Data data, Meta meta) {

    public record Data(
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

    public record Meta(
            OffsetDateTime asOf,
            String disclaimer,
            String symbol,
            String source,
            boolean stale,
            String spreadSource
    ) {}
}
