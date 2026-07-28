package com.orderflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
@IdClass(ProcessedEventId.class)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(name = "consumer_name", length = 120)
    private String consumerName;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    protected ProcessedEvent() {}

    public ProcessedEvent(
            UUID eventId,
            String eventType,
            UUID aggregateId,
            String consumerName,
            Instant processedAt,
            String correlationId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.consumerName = consumerName;
        this.processedAt = processedAt;
        this.correlationId = correlationId;
    }
}
