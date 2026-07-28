package com.orderflow.inventory.error;

import com.orderflow.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InventoryDomainException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public InventoryDomainException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public InventoryDomainException(
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
