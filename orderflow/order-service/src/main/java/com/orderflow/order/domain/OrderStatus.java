package com.orderflow.order.domain;

public enum OrderStatus {
    INVENTORY_PENDING,
    INVENTORY_RESERVED,
    INVENTORY_REJECTED,
    PAYMENT_PENDING,
    CANCEL_REQUESTED,
    CANCELLED
}
