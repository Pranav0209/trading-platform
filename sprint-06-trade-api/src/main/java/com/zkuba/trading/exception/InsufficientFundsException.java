package com.zkuba.trading.exception;

public class InsufficientFundsException extends com.zkuba.trading.domain.exception.InsufficientFundsException {
    public InsufficientFundsException(Long accountId) {
        super(accountId);
    }
}
