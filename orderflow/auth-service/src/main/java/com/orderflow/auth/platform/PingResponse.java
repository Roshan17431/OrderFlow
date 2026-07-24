package com.orderflow.auth.platform;

import java.time.Instant;

public record PingResponse(String service, String status, Instant timestamp, String correlationId) {
}
