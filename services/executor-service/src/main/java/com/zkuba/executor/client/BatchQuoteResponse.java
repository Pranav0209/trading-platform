package com.zkuba.executor.client;

import java.util.List;

public record BatchQuoteResponse(Data data, QuoteResponse.Meta meta) {

    public record Data(List<QuoteItem> quotes) {}

    public record QuoteItem(
            String symbol,
            String source,
            boolean stale,
            QuoteResponse.Data quote
    ) {}
}
