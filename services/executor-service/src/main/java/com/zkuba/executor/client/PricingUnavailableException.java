package com.zkuba.executor.client;

public class PricingUnavailableException extends RuntimeException {
    public PricingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
