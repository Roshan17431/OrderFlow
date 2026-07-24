package com.orderflow.platform.error;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> fieldErrors) {

    public ApiError {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
