package com.orderflow.auth.error;

public class SecurityStateUnavailableException extends RuntimeException {

    public SecurityStateUnavailableException(Throwable cause) {
        super("Security state is temporarily unavailable.", cause);
    }
}
