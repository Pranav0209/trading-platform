package com.zkuba.trading.exception;

public class InstrumentNotFoundException extends NotFoundException {
    private final String symbol;

    public InstrumentNotFoundException(String symbol) {
        super("INS-404", "Instrument not found: " + symbol);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
