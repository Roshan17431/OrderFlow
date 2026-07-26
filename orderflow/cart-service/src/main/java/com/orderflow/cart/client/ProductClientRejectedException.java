package com.orderflow.cart.client;

import org.springframework.http.HttpStatusCode;

public class ProductClientRejectedException extends RuntimeException {

    private final int status;
    private final String downstreamCode;

    public ProductClientRejectedException(HttpStatusCode status) {
        this(status, null);
    }

    public ProductClientRejectedException(HttpStatusCode status, String downstreamCode) {
        super("Product service rejected the resolution request");
        this.status = status.value();
        this.downstreamCode = downstreamCode;
    }

    public int status() {
        return status;
    }

    public String downstreamCode() {
        return downstreamCode;
    }
}
