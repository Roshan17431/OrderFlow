package com.orderflow.order.domain;

public enum IdempotencyScope {
    CREATE_ORDER,
    CANCEL_ORDER
}
