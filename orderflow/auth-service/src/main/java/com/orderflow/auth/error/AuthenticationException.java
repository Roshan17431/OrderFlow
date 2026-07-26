package com.orderflow.auth.error;

import com.orderflow.platform.error.ErrorCode;

public class AuthenticationException extends RuntimeException {

    private final ErrorCode code;

    public AuthenticationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
