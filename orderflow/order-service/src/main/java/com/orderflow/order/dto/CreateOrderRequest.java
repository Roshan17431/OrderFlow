package com.orderflow.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @Min(0) long expectedCartVersion,
        @Valid @NotNull ShippingAddressRequest shippingAddress) {}
