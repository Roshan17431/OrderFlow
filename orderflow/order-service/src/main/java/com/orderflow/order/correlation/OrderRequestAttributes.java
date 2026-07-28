package com.orderflow.order.correlation;

public final class OrderRequestAttributes {
    public static final String CORRELATION_ID =
            OrderRequestAttributes.class.getName() + ".correlationId";

    private OrderRequestAttributes() {}
}
