package com.orderflow.cart.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.clients.product.resilience")
public record ProductResilienceProperties(
        @Min(1) @Max(5) @DefaultValue("2") int maxAttempts,
        @NotNull @DefaultValue("200ms") Duration retryWait,
        @Min(2) @DefaultValue("20") int slidingWindowSize,
        @Min(1) @DefaultValue("10") int minimumCalls,
        @Min(1) @Max(100) @DefaultValue("50") float failureRateThreshold,
        @NotNull @DefaultValue("30s") Duration openStateDuration) {

    @AssertTrue(message = "product resilience durations must be positive")
    public boolean areDurationsValid() {
        return positive(retryWait) && positive(openStateDuration);
    }

    @AssertTrue(message = "minimum calls cannot exceed the sliding window")
    public boolean isWindowValid() {
        return minimumCalls <= slidingWindowSize;
    }

    private boolean positive(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }
}
