package com.zkuba.trading.exception;

public class InsufficientFundsException extends BadRequestException {
    private final Long accountId;

    public InsufficientFundsException(Long accountId) {
        super("ORD-400", "Insufficient funds for account: " + accountId);
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}
