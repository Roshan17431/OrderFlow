package com.orderflow.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventMetadata(
        UUID eventId,
        EventType eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        String correlationId,
        UUID causationId,
        String producer) {

    public EventMetadata {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        aggregateType = requireText(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        correlationId = requireText(correlationId, "correlationId");
        producer = requireText(producer, "producer");
    }

    public void require(EventType expectedType, int supportedVersion) {
        if (eventType != expectedType) {
            throw new IllegalArgumentException("Unexpected event type");
        }
        if (eventVersion != supportedVersion) {
            throw new UnsupportedEventVersionException(eventType, eventVersion);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
