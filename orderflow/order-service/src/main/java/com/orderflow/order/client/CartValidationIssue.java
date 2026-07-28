package com.orderflow.order.client;

import java.util.UUID;

public record CartValidationIssue(UUID productId, String code, String message) {}
