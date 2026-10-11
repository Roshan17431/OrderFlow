package com.orderflow.payment.domain;

public enum PaymentAttemptStatus {
    CREATED,
    SUBMITTED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
