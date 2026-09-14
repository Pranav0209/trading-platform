package com.zkuba.trading.exception;

public class AccountAlreadyExistsException extends ConflictException {
    private final Long userId;

    public AccountAlreadyExistsException(Long userId) {
        super("ACC-409", "Trading account already exists for user " + userId);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
