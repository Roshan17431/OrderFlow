package com.orderflow.notification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.notification")
public record NotificationProperties(
        @NotBlank String emailProvider,
        @NotBlank String smsProvider,
        @Valid Async async,
        @NotNull Duration sseTimeout,
        @Min(1) @Max(100) int maxPageSize) {
    public record Async(
            @Min(1) int corePoolSize,
            @Min(1) int maxPoolSize,
            @Min(1) int queueCapacity) {
        public Async {
            if (maxPoolSize < corePoolSize) {
                throw new IllegalArgumentException("max pool must be >= core pool");
            }
        }
    }
}
