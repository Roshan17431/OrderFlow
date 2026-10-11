package com.orderflow.payment.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentStateMachine {

    private final Map<PaymentStatus, Set<PaymentStatus>> transitions;

    public PaymentStateMachine() {
        Map<PaymentStatus, Set<PaymentStatus>> allowed = new EnumMap<>(PaymentStatus.class);
        allowed.put(PaymentStatus.PENDING, EnumSet.of(
                PaymentStatus.PROCESSING, PaymentStatus.CANCEL_REQUESTED,
                PaymentStatus.CANCELLED));
        allowed.put(PaymentStatus.REQUIRES_ACTION, EnumSet.of(
                PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED,
                PaymentStatus.FAILED, PaymentStatus.CANCEL_REQUESTED));
        allowed.put(PaymentStatus.PROCESSING, EnumSet.of(
                PaymentStatus.REQUIRES_ACTION, PaymentStatus.SUCCEEDED,
                PaymentStatus.FAILED, PaymentStatus.CANCEL_REQUESTED));
        allowed.put(PaymentStatus.FAILED, EnumSet.of(
                PaymentStatus.PROCESSING, PaymentStatus.CANCELLED));
        allowed.put(PaymentStatus.CANCEL_REQUESTED, EnumSet.of(
                PaymentStatus.CANCELLED, PaymentStatus.REFUND_PENDING));
        allowed.put(PaymentStatus.SUCCEEDED, EnumSet.of(PaymentStatus.REFUND_PENDING));
        allowed.put(PaymentStatus.PARTIALLY_REFUNDED, EnumSet.of(
                PaymentStatus.REFUND_PENDING));
        allowed.put(PaymentStatus.REFUND_PENDING, EnumSet.of(
                PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED,
                PaymentStatus.REFUNDED));
        allowed.put(PaymentStatus.CANCELLED, EnumSet.of(PaymentStatus.REFUND_PENDING));
        allowed.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
        transitions = Map.copyOf(allowed);
    }

    public void transition(Payment payment, PaymentStatus next, Instant now) {
        if (!canTransition(payment.getStatus(), next)) {
            throw new PaymentStateConflictException(payment.getStatus(), next);
        }
        payment.changeStatus(next, now);
    }

    public boolean canTransition(PaymentStatus current, PaymentStatus next) {
        return transitions.getOrDefault(current, Set.of()).contains(next);
    }
}
