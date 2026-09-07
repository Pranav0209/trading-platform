package com.zkuba.trading.domain.exception;

public class InsufficientHoldingsException extends GlobalExceptionHandler {

    private final Long accountId;
    private final String symbol;

    public InsufficientHoldingsException(Long accountId, String symbol) {
        super("ORD-410", "Insufficient holdings");
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