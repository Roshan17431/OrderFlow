package com.orderflow.payment.domain;

public class PaymentStateConflictException extends IllegalStateException {
    public PaymentStateConflictException(PaymentStatus current, PaymentStatus next) {
        super("Payment cannot transition from " + current + " to " + next);
    }
}
