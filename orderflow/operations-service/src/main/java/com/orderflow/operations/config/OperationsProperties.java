package com.orderflow.operations.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.operations")
public record OperationsProperties(
        @Min(1) @Max(100) int maxPageSize,
        @Valid Reporting reporting) {
    public record Reporting(
            boolean enabled,
            @NotBlank String cron,
            @NotBlank String zone,
            @NotNull Duration lockDuration,
            @Min(1) @Max(31) int maxCatchupDays) {}
}
