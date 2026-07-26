package com.orderflow.auth.security.refresh;

public record IssuedRefreshToken(String value, RefreshTokenMetadata metadata) {
}
