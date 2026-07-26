package com.orderflow.cart.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.cart")
public record CartProperties(
        @NotNull @DefaultValue("30d") Duration ttl,
        @Min(1) @Max(1000) @DefaultValue("100") int maxDistinctItems,
        @Min(1) @Max(10000) @DefaultValue("99") int maxQuantityPerItem,
        @NotBlank @DefaultValue("orderflow:cart:") String keyPrefix) {

    @AssertTrue(message = "cart TTL must be positive")
    public boolean isTtlValid() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
