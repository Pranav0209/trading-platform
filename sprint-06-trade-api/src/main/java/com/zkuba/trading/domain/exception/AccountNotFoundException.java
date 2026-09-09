package com.zkuba.trading.domain.exception;

public class AccountNotFoundException extends NotFoundException {
    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super("ACC-404", "Account not found: " + accountId);
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}
