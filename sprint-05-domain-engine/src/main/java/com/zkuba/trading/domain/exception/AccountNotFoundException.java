package com.zkuba.trading.domain.exception;

public class AccountNotFoundException extends GlobalExceptionHandler {

    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super("ACC-404", "Account not found");
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}