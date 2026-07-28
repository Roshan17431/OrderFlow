package com.orderflow.events;

public final class UnsupportedEventVersionException extends IllegalArgumentException {

    private final EventType eventType;
    private final int eventVersion;

    public UnsupportedEventVersionException(EventType eventType, int eventVersion) {
        super("Unsupported event version");
        this.eventType = eventType;
        this.eventVersion = eventVersion;
    }

    public EventType eventType() {
        return eventType;
    }

    public int eventVersion() {
        return eventVersion;
    }
}
