package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record NotificationDeliveredEvent(EventMetadata metadata, UUID notificationId, UUID userId, String channel, UUID deliveryId, Instant deliveredAt) {
    public static final int VERSION = 1;
    public NotificationDeliveredEvent { Objects.requireNonNull(metadata); metadata.require(EventType.NOTIFICATION_DELIVERED, VERSION); Objects.requireNonNull(notificationId); Objects.requireNonNull(userId); Objects.requireNonNull(deliveryId); Objects.requireNonNull(deliveredAt); }
}
