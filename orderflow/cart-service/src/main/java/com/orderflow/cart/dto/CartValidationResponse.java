package com.orderflow.cart.dto;

import java.util.List;

public record CartValidationResponse(
        CartResponse cart, boolean valid, List<CartValidationIssue> issues) {

    public CartValidationResponse {
        issues = List.copyOf(issues);
    }
}
