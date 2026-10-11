package com.orderflow.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(
        @NotBlank(message = "paymentMethodToken is required")
        @Size(max = 128)
        String paymentMethodToken) {}
