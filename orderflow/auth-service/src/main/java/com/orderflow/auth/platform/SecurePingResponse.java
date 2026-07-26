package com.orderflow.auth.platform;

import java.time.Instant;
import java.util.List;

public record SecurePingResponse(
        String service,
        String status,
        String userId,
        String email,
        List<String> roles,
        String correlationId,
        Instant timestamp) {
}
