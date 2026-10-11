package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record DeliveryAssignedEvent(EventMetadata metadata, UUID deliveryId, UUID orderId, UUID customerId, UUID deliveryAgentId, Instant assignedAt) {
    public static final int VERSION = 1;
    public DeliveryAssignedEvent { DeliveryRequestedEvent.validate(metadata, EventType.DELIVERY_ASSIGNED, orderId); Objects.requireNonNull(deliveryId); Objects.requireNonNull(customerId); Objects.requireNonNull(deliveryAgentId); Objects.requireNonNull(assignedAt); }
}
