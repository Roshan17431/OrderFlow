package com.orderflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record CreateRefundRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2)
        BigDecimal amount,
        @NotBlank @Pattern(regexp =
                "CUSTOMER_REQUEST|DUPLICATE_PAYMENT|ORDER_CANCELLED|SERVICE_FAILURE|SYSTEM_COMPENSATION|OTHER")
        String reason) {}
