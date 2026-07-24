package com.orderflow.platform.http;

public final class CorrelationHeaders {

    public static final String CORRELATION_ID = "X-Correlation-ID";

    private CorrelationHeaders() {
        throw new AssertionError("Constants class must not be instantiated");
    }
}
