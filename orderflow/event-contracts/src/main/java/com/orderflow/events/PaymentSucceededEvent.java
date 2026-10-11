package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentSucceededEvent(
        EventMetadata metadata,
        UUID paymentId,
        UUID orderId,
        UUID customerId,
        String provider,
        String providerPaymentId,
        BigDecimal amount,
        String currency,
        Instant succeededAt) {

    public static final int VERSION = 1;

    public PaymentSucceededEvent {
        PaymentRequestedEvent.validate(metadata, EventType.PAYMENT_SUCCEEDED, orderId);
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        provider = requireText(provider, "provider");
        providerPaymentId = requireText(providerPaymentId, "providerPaymentId");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
        Objects.requireNonNull(succeededAt, "succeededAt is required");
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
