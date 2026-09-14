package com.zkuba.trading.exception;

public class MarketDataException extends BadRequestException {
    public MarketDataException(String symbol) {
        super("MKT-400", "Market data not available for: " + symbol);
    }
}
