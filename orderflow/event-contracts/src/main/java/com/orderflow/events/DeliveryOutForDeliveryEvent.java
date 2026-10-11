package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record DeliveryOutForDeliveryEvent(EventMetadata metadata, UUID deliveryId, UUID orderId, UUID customerId, UUID deliveryAgentId, Instant outForDeliveryAt) {
    public static final int VERSION = 1;
    public DeliveryOutForDeliveryEvent { DeliveryRequestedEvent.validate(metadata, EventType.DELIVERY_OUT_FOR_DELIVERY, orderId); Objects.requireNonNull(deliveryId); Objects.requireNonNull(customerId); Objects.requireNonNull(deliveryAgentId); Objects.requireNonNull(outForDeliveryAt); }
}
