package com.orderflow.order.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderStateMachine {

    private final Map<OrderStatus, Set<OrderStatus>> transitions;

    public OrderStateMachine() {
        Map<OrderStatus, Set<OrderStatus>> allowed = new EnumMap<>(OrderStatus.class);
        allowed.put(
                OrderStatus.INVENTORY_PENDING,
                EnumSet.of(
                        OrderStatus.INVENTORY_RESERVED,
                        OrderStatus.INVENTORY_REJECTED,
                        OrderStatus.CANCEL_REQUESTED));
        allowed.put(
                OrderStatus.INVENTORY_RESERVED,
                EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCEL_REQUESTED));
        allowed.put(
                OrderStatus.PAYMENT_PENDING,
                EnumSet.of(
                        OrderStatus.PAID,
                        OrderStatus.PAYMENT_FAILED,
                        OrderStatus.CANCEL_REQUESTED,
                        OrderStatus.CANCELLED));
        allowed.put(OrderStatus.CANCEL_REQUESTED, EnumSet.of(OrderStatus.CANCELLED));
        allowed.put(
                OrderStatus.PAID,
                EnumSet.of(OrderStatus.PARTIALLY_REFUNDED, OrderStatus.REFUNDED));
        allowed.put(
                OrderStatus.PARTIALLY_REFUNDED,
                EnumSet.of(OrderStatus.PARTIALLY_REFUNDED, OrderStatus.REFUNDED));
        allowed.put(OrderStatus.PAYMENT_FAILED, EnumSet.noneOf(OrderStatus.class));
        allowed.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
        allowed.put(OrderStatus.INVENTORY_REJECTED, EnumSet.noneOf(OrderStatus.class));
        allowed.put(OrderStatus.CANCELLED, EnumSet.of(OrderStatus.REFUNDED));
        transitions = Map.copyOf(allowed);
    }

    public void transition(
            CustomerOrder order,
            OrderStatus next,
            String reason,
            UUID eventId,
            String correlationId,
            Instant now) {
        if (!canTransition(order.getStatus(), next)) {
            throw new OrderStateConflictException(order.getStatus(), next);
        }
        order.transition(next, reason, eventId, correlationId, now);
    }

    public boolean canTransition(OrderStatus current, OrderStatus next) {
        return transitions.getOrDefault(current, Set.of()).contains(next);
    }
}
