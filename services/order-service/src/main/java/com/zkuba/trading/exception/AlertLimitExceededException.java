package com.zkuba.trading.exception;

public class AlertLimitExceededException extends ConflictException {
    private final long limit;

    public AlertLimitExceededException(long limit) {
        super("ALT-409", "Alert limit (" + limit + ") exceeded");
        this.limit = limit;
    }

    public long getLimit() {
        return limit;
    }
}
