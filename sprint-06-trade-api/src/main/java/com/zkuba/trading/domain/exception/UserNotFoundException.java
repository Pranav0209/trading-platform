package com.zkuba.trading.domain.exception;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(Long userId) {
        super("USR-404", "User not found: " + userId);
    }
}
