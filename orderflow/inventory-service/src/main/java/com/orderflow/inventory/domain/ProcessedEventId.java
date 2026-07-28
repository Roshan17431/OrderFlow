package com.orderflow.inventory.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class ProcessedEventId implements Serializable {
    private UUID eventId;
    private String consumerName;

    public ProcessedEventId() {}

    public ProcessedEventId(UUID eventId, String consumerName) {
        this.eventId = eventId;
        this.consumerName = consumerName;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProcessedEventId that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId)
                && Objects.equals(consumerName, that.consumerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumerName);
    }
}
