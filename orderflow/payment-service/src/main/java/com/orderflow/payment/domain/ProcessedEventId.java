package com.orderflow.payment.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class ProcessedEventId implements Serializable {
    private UUID eventId;
    private String consumerName;

    public ProcessedEventId() {}

    @Override
    public boolean equals(Object other) {
        return other instanceof ProcessedEventId that
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(consumerName, that.consumerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumerName);
    }
}
