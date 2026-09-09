package com.zkuba.trading.exception;

public class UserNotFoundException extends NotFoundException {
    private final Long userId;

    public UserNotFoundException(Long userId) {
        super("USR-404", "User not found: " + userId);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
