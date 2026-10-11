package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record DeliveryCancelRequestedEvent(EventMetadata metadata, UUID orderId, UUID customerId, UUID deliveryId, String reasonCode, Instant requestedAt) {
    public static final int VERSION = 1;
    public DeliveryCancelRequestedEvent { DeliveryRequestedEvent.validate(metadata, EventType.DELIVERY_CANCEL_REQUESTED, orderId); Objects.requireNonNull(customerId); if (reasonCode == null || reasonCode.isBlank() || reasonCode.length() > 80) throw new IllegalArgumentException("reasonCode is invalid"); Objects.requireNonNull(requestedAt); }
}
