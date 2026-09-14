package com.zkuba.trading.exception;

public class AccountNotActiveException extends ForbiddenException {
    private final Long accountId;

    public AccountNotActiveException(Long accountId) {
        super("ACC-403", "Account not active: " + accountId);
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}
