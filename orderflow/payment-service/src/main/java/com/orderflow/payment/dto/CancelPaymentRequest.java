package com.orderflow.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CancelPaymentRequest(
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "[A-Z][A-Z0-9_]*")
        String reason) {}
