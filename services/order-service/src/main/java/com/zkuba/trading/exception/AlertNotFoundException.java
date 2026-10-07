package com.zkuba.trading.exception;

public class AlertNotFoundException extends NotFoundException {
    private final Long alertId;

    public AlertNotFoundException(Long alertId) {
        super("ALT-404", "Alert not found: " + alertId);
        this.alertId = alertId;
    }

    public Long getAlertId() {
        return alertId;
    }
}
