package com.orderflow.auth.dto.response;

public record AuthenticationResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
