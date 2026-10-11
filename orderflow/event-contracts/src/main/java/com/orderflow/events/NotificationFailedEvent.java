package com.orderflow.events;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record NotificationFailedEvent(EventMetadata metadata, UUID notificationId, UUID userId, String channel, UUID deliveryId, String failureCode, Instant failedAt) {
    public static final int VERSION = 1;
    public NotificationFailedEvent { Objects.requireNonNull(metadata); metadata.require(EventType.NOTIFICATION_FAILED, VERSION); Objects.requireNonNull(notificationId); Objects.requireNonNull(userId); Objects.requireNonNull(deliveryId); Objects.requireNonNull(failedAt); if (failureCode == null || failureCode.isBlank() || failureCode.length() > 80) throw new IllegalArgumentException("failureCode is invalid"); }
}
