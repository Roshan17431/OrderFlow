package com.orderflow.auth.audit;

import java.time.Instant;
import java.util.UUID;

public record SecurityAuditEvent(
        Event event,
        Instant timestamp,
        UUID userId,
        UUID sessionId,
        String provider,
        String correlationId,
        String ipHash,
        String userAgentHash,
        String reasonCode) {

    public enum Event {
        USER_REGISTERED,
        LOGIN_SUCCEEDED,
        LOGIN_FAILED,
        LOGIN_THROTTLED,
        TOKEN_REFRESHED,
        REFRESH_TOKEN_REUSE_DETECTED,
        LOGOUT_SUCCEEDED,
        LOGOUT_ALL_SUCCEEDED,
        OAUTH_LOGIN_SUCCEEDED,
        OAUTH_LOGIN_FAILED,
        ACCESS_DENIED,
        TOKEN_REJECTED
    }
}
