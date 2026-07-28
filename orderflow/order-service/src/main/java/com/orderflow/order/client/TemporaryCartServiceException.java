package com.orderflow.order.client;

public final class TemporaryCartServiceException extends RuntimeException {
    public TemporaryCartServiceException(String message) {
        super(message);
    }

    public TemporaryCartServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
