package com.orderflow.payment.correlation;

public final class PaymentRequestAttributes {
    public static final String CORRELATION_ID =
            PaymentRequestAttributes.class.getName() + ".correlationId";
    private PaymentRequestAttributes() {}
}
