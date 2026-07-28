package com.orderflow.order.client;

import java.util.List;

public record CartValidationResult(
        CartSnapshot cart, boolean valid, List<CartValidationIssue> issues) {
    public CartValidationResult {
        issues = List.copyOf(issues);
    }
}
