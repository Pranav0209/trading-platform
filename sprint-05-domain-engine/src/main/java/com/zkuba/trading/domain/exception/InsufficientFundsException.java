package com.zkuba.trading.domain.exception;

public class InsufficientFundsException extends GlobalExceptionHandler {

    private final Long accountId;

    public InsufficientFundsException(Long accountId) {
        super("ORD-400", "Insufficient funds");
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}