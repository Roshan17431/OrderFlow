package com.orderflow.operations.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.scheduler")
public record SchedulerProperties(
        @NotBlank String instanceId,
        @NotNull Duration retentionDelay,
        @NotNull Duration retentionLockDuration,
        @NotNull Duration processedEventRetention,
        @NotNull Duration jobHistoryRetention,
        @Min(1) @Max(10000) int retentionBatchSize) {}
