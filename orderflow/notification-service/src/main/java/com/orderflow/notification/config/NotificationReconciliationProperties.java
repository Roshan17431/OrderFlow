package com.orderflow.notification.config;
import jakarta.validation.constraints.*;import java.time.Duration;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.validation.annotation.Validated;
@Validated @ConfigurationProperties("app.notification.reconciliation") public record NotificationReconciliationProperties(boolean enabled,@NotNull Duration delay,@Min(1) @Max(500) int batchSize,@NotNull Duration staleThreshold,@Min(1) @Max(100) int maxAttempts,@NotNull Duration lockDuration){}
