package com.orderflow.order.client;

import java.util.UUID;

public record CartValidationIssue(UUID productId, String code, String message) {
    public CartValidationIssue {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
    }
}
