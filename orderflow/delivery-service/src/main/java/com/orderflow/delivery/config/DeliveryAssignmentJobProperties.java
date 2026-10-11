package com.orderflow.delivery.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.delivery.assignment-job")
public record DeliveryAssignmentJobProperties(boolean enabled, @NotNull Duration delay,
        @Min(1) @Max(500) int batchSize, @NotNull Duration lockDuration) {}
