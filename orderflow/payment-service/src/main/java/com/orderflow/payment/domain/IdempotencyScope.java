package com.orderflow.payment.domain;

public enum IdempotencyScope {
    CONFIRM_PAYMENT,
    CANCEL_PAYMENT,
    CREATE_REFUND
}
