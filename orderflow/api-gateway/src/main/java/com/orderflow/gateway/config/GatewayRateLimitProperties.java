package com.orderflow.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.rate-limit")
public record GatewayRateLimitProperties(
        boolean enabled,
        @NotBlank String namespace,
        @Valid @NotNull Policy login,
        @Valid @NotNull Policy registration,
        @Valid @NotNull Policy refresh,
        @Valid @NotNull Policy orderCreation,
        @Valid @NotNull Policy paymentConfirmation,
        @Valid @NotNull Policy webhook) {

    public record Policy(@Min(1) @Max(10000) int requests, @NotNull Duration window) {
        public Policy {
            if (window != null && (window.isZero() || window.isNegative())) {
                throw new IllegalArgumentException("rate-limit window must be positive");
            }
        }
    }
}
