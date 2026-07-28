package com.orderflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private OrderStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private OrderStatus newStatus;

    @Column(name = "reason_code", length = 120)
    private String reasonCode;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected OrderStatusHistory() {}

    OrderStatusHistory(
            CustomerOrder order,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            String reasonCode,
            UUID eventId,
            String correlationId,
            Instant changedAt) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reasonCode = reasonCode;
        this.eventId = eventId;
        this.correlationId = correlationId;
        this.changedAt = changedAt;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
