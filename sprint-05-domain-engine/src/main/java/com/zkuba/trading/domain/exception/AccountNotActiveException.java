package com.zkuba.trading.domain.exception;

public class AccountNotActiveException extends GlobalExceptionHandler {

    private final Long accountId;

    public AccountNotActiveException(Long accountId) {
        super("ACC-403", "Account not active");
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}