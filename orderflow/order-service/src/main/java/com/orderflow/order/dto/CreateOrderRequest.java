package com.orderflow.order.dto;

import jakarta.validation.constraints.Min;

public record CreateOrderRequest(@Min(0) long expectedCartVersion) {}
