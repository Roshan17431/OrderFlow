package com.orderflow.auth.correlation;

public final class CorrelationRequestAttributes {

    public static final String CORRELATION_ID =
            CorrelationRequestAttributes.class.getName() + ".correlationId";

    private CorrelationRequestAttributes() {
        throw new AssertionError("Constants class must not be instantiated");
    }
}
