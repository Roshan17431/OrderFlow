package com.orderflow.payment.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.payment")
public record PaymentProperties(
        @NotBlank String provider,
        boolean simulatorEnabled,
        boolean simulatorAllowInProduction,
        @NotBlank String simulatorWebhookSecret,
        @NotNull Duration webhookTolerance,
        @NotNull Duration providerConnectTimeout,
        @NotNull Duration providerReadTimeout,
        @Min(1) @Max(5) int providerRetryAttempts,
        @NotNull Duration providerRetryWait,
        @Min(1) @Max(100) int maxPageSize) {}
