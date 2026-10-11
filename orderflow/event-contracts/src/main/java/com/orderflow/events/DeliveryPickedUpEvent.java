package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record DeliveryPickedUpEvent(EventMetadata metadata, UUID deliveryId, UUID orderId, UUID customerId, UUID deliveryAgentId, Instant pickedUpAt) {
    public static final int VERSION = 1;
    public DeliveryPickedUpEvent { DeliveryRequestedEvent.validate(metadata, EventType.DELIVERY_PICKED_UP, orderId); Objects.requireNonNull(deliveryId); Objects.requireNonNull(customerId); Objects.requireNonNull(deliveryAgentId); Objects.requireNonNull(pickedUpAt); }
}
