package com.zkuba.trading.exception;

public class InsufficientHoldingsException extends ConflictException {
    private final Long accountId;
    private final String symbol;

    public InsufficientHoldingsException(Long accountId, String symbol) {
        super("ORD-409", "Insufficient holdings for " + symbol + " in account " + accountId);
        this.accountId = accountId;
        this.symbol = symbol;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }
}
