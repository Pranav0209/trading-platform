package com.zkuba.trading.client;

import java.util.List;

public record CandleResponse(
        Data data,
        Meta meta
) {
    public record Data(
            String symbol,
            String currency,
            String interval,
            List<Candle> candles
    ) {}

    public record Meta(
            String symbol,
            String source,
            Long lastRefreshed
    ) {}

    public record Candle(
            String date,
            Double open,
            Double high,
            Double low,
            Double close,
            Double adjclose,
            Long volume,
            Boolean synthetic
    ) {}
}
