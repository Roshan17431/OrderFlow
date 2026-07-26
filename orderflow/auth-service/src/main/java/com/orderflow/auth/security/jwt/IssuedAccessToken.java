package com.orderflow.auth.security.jwt;

import java.time.Instant;

public record IssuedAccessToken(String value, String jti, Instant expiresAt) {
}
