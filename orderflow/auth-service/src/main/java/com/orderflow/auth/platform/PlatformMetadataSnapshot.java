package com.orderflow.auth.platform;

import java.time.Instant;

public record PlatformMetadataSnapshot(String serviceName, String schemaVersion, Instant createdAt) {
}
