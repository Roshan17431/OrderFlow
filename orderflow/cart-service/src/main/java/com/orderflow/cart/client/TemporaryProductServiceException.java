package com.orderflow.cart.client;

public class TemporaryProductServiceException extends RuntimeException {

    public TemporaryProductServiceException(String message) {
        super(message);
    }

    public TemporaryProductServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
