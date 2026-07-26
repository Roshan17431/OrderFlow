package com.orderflow.auth.security.refresh;

public record RefreshRotationResult(Status status, IssuedRefreshToken replacement) {

    public enum Status {
        ROTATED,
        INVALID,
        REUSE_DETECTED
    }
}
