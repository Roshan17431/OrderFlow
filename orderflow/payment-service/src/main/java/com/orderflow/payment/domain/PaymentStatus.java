package com.orderflow.payment.domain;

public enum PaymentStatus {
    PENDING,
    REQUIRES_ACTION,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED,
    REFUND_PENDING,
    PARTIALLY_REFUNDED,
    REFUNDED
}
