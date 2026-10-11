package com.orderflow.payment.service;

import com.orderflow.platform.error.ErrorCode;

public record CommandResult<T>(
        T body,
        int status,
        ErrorCode errorCode,
        String errorMessage) {
    public static <T> CommandResult<T> success(T body, int status) {
        return new CommandResult<>(body, status, null, null);
    }
    public static <T> CommandResult<T> error(
            T body, int status, ErrorCode code, String message) {
        return new CommandResult<>(body, status, code, message);
    }
    public boolean failed() { return errorCode != null; }
}
