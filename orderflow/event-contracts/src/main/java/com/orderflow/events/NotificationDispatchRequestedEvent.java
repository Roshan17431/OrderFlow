package com.orderflow.events;
import java.util.Objects;
import java.util.UUID;
public record NotificationDispatchRequestedEvent(EventMetadata metadata, UUID notificationId, UUID userId, String channel, UUID deliveryId) {
    public static final int VERSION = 1;
    public NotificationDispatchRequestedEvent { Objects.requireNonNull(metadata); metadata.require(EventType.NOTIFICATION_DISPATCH_REQUESTED, VERSION); Objects.requireNonNull(notificationId); Objects.requireNonNull(userId); Objects.requireNonNull(deliveryId); if (!"EMAIL".equals(channel) && !"SMS".equals(channel)) throw new IllegalArgumentException("channel is invalid"); }
}
