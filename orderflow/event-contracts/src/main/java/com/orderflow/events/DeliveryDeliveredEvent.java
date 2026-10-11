package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record DeliveryDeliveredEvent(EventMetadata metadata, UUID deliveryId, UUID orderId, UUID customerId, UUID deliveryAgentId, Instant deliveredAt) {
    public static final int VERSION = 1;
    public DeliveryDeliveredEvent { DeliveryRequestedEvent.validate(metadata, EventType.DELIVERY_DELIVERED, orderId); Objects.requireNonNull(deliveryId); Objects.requireNonNull(customerId); Objects.requireNonNull(deliveryAgentId); Objects.requireNonNull(deliveredAt); }
}
