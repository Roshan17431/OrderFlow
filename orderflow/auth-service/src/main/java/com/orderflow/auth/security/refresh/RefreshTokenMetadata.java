package com.orderflow.auth.security.refresh;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenMetadata(
        UUID sessionId,
        UUID familyId,
        UUID userId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        boolean rotated,
        boolean revoked,
        String replacedBy,
        String userAgentHash,
        String ipHash,
        long tokenVersion) {
}
