package com.orderflow.payment.provider;

public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException() {
        super("The payment provider is temporarily unavailable");
    }
}
