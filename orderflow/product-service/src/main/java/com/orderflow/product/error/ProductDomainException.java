package com.orderflow.product.error;

import com.orderflow.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ProductDomainException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ProductDomainException(HttpStatus status, ErrorCode code, String message) {
        super(message);
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
