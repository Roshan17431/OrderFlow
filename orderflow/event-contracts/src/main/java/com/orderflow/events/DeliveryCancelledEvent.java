package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record DeliveryCancelledEvent(EventMetadata metadata, UUID deliveryId, UUID orderId, UUID customerId, UUID deliveryAgentId, String reasonCode, Instant cancelledAt) {
    public static final int VERSION = 1;
    public DeliveryCancelledEvent { DeliveryRequestedEvent.validate(metadata, EventType.DELIVERY_CANCELLED, orderId); Objects.requireNonNull(deliveryId); Objects.requireNonNull(customerId); if (reasonCode == null || reasonCode.isBlank() || reasonCode.length() > 80) throw new IllegalArgumentException("reasonCode is invalid"); Objects.requireNonNull(cancelledAt); }
}
