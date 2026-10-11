package com.orderflow.payment.config;
import jakarta.validation.constraints.*;import java.time.Duration;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.validation.annotation.Validated;
@Validated @ConfigurationProperties("app.payment.reconciliation") public record PaymentReconciliationProperties(boolean enabled,@NotNull Duration delay,@Min(1) @Max(500) int batchSize,@NotNull Duration lockDuration,@NotNull Duration maxAge){}
