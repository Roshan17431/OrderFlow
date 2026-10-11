package com.orderflow.order.client;

import java.util.List;
import java.util.Objects;

public record CartValidationResult(
        CartSnapshot cart, boolean valid, List<CartValidationIssue> issues) {
    public CartValidationResult {
        Objects.requireNonNull(cart, "cart is required");
        issues = List.copyOf(Objects.requireNonNull(issues, "issues are required"));
        if (valid && !issues.isEmpty()) throw new IllegalArgumentException("a valid cart cannot contain issues");
    }
}
