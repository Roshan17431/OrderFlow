package com.orderflow.order.domain;

public final class OrderStateConflictException extends IllegalStateException {
    public OrderStateConflictException(OrderStatus current, OrderStatus requested) {
        super("Order cannot transition from " + current + " to " + requested);
    }
}
