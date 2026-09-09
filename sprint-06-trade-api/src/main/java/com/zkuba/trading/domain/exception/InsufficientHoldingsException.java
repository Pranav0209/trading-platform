package com.zkuba.trading.domain.exception;

public class InsufficientHoldingsException extends UnprocessableEntityException {
    private final Long accountId;
    private final String symbol;

    public InsufficientHoldingsException(Long accountId, String symbol) {
        super("ORD-410", "Insufficient holdings for " + symbol + " in account " + accountId);
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
