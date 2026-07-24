package com.orderflow.gateway.correlation;

public final class GatewayCorrelationContext {

    public static final String EXCHANGE_ATTRIBUTE =
            GatewayCorrelationContext.class.getName() + ".correlationId";
    public static final String REACTOR_CONTEXT_KEY = "orderflow.correlationId";

    private GatewayCorrelationContext() {
        throw new AssertionError("Constants class must not be instantiated");
    }
}
