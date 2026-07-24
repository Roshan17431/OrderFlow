package com.orderflow.auth.platform;

import java.time.Instant;

public record PlatformInfoResponse(
        String service,
        String schemaVersion,
        Instant createdAt,
        String correlationId) {
}
