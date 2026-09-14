package com.zkuba.trading.dto.response;

public record InstrumentResponse(
        String symbol, String displayName, String assetClass, String currency, boolean tradable) {}
