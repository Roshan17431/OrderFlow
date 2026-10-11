package com.orderflow.inventory.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.inventory")
public record InventoryProperties(
        @NotNull Duration reservationTtl,
        @NotNull Duration expirationPollDelay,
        @NotNull Duration expirationLockDuration,
        @Min(1) int expirationBatchSize,
        boolean expirationEnabled,
        @Min(1) int maxHistoryPageSize) {}
