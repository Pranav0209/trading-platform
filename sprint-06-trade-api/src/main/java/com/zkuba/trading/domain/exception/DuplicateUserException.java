package com.zkuba.trading.domain.exception;

public class DuplicateUserException extends ConflictException {
    public DuplicateUserException() {
        super("USR-409", "Username or email already exists");
    }
}
