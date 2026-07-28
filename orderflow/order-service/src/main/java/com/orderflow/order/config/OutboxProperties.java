package com.orderflow.order.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.outbox")
public record OutboxProperties(
        @NotNull Duration pollDelay,
        @Min(1) int batchSize,
        @Min(1) int maxAttempts,
        @NotNull Duration lockTimeout,
        @NotNull Duration retryBaseDelay,
        @NotNull Duration sendTimeout) {}
