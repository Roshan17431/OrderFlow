package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
public record DeliveryFailedEvent(EventMetadata metadata, UUID deliveryId, UUID orderId, UUID customerId, UUID deliveryAgentId, String failureCode, boolean retryable, Instant failedAt) {
    public static final int VERSION = 1;
    private static final Set<String> CODES = Set.of("CUSTOMER_UNAVAILABLE", "ADDRESS_PROBLEM", "PACKAGE_DAMAGED", "VEHICLE_PROBLEM", "OTHER");
    public DeliveryFailedEvent { DeliveryRequestedEvent.validate(metadata, EventType.DELIVERY_FAILED, orderId); Objects.requireNonNull(deliveryId); Objects.requireNonNull(customerId); Objects.requireNonNull(deliveryAgentId); if (!CODES.contains(failureCode)) throw new IllegalArgumentException("failureCode is invalid"); Objects.requireNonNull(failedAt); }
}
