package com.orderflow.order.domain;

public enum FulfillmentStatus {
    NOT_REQUESTED,
    DELIVERY_PENDING,
    DELIVERY_ASSIGNED,
    PICKED_UP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
    DELIVERY_CANCEL_REQUESTED,
    DELIVERY_CANCELLED
}
