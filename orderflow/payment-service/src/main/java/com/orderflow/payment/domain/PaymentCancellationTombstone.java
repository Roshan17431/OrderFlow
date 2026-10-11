package com.orderflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_cancellation_tombstone")
public class PaymentCancellationTombstone {
    @Id @Column(name = "order_id") private UUID orderId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "payment_id") private UUID paymentId;
    @Column(name = "reason_code", nullable = false, length = 80) private String reasonCode;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "correlation_id", nullable = false, length = 128) private String correlationId;

    protected PaymentCancellationTombstone() {}

    public PaymentCancellationTombstone(
            UUID orderId, UUID customerId, UUID paymentId, String reasonCode,
            Instant requestedAt, String correlationId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.paymentId = paymentId;
        this.reasonCode = reasonCode;
        this.requestedAt = requestedAt;
        this.correlationId = correlationId;
    }
}
