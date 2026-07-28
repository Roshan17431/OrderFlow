package com.orderflow.order.error;

import com.orderflow.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class OrderDomainException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public OrderDomainException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public OrderDomainException(
            HttpStatus status, ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }
}
