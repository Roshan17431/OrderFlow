package com.orderflow.cart.dto;

import java.util.UUID;

public record CartValidationIssue(UUID productId, String code, String message) {}
