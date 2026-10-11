package com.orderflow.payment.provider;

public class ProviderTimeoutException extends RuntimeException {
    public ProviderTimeoutException() {
        super("The payment provider timed out");
    }
}
