package com.orderflow.product.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.catalog.cache")
public record CatalogCacheProperties(
        @NotBlank @DefaultValue("orderflow:catalog:v1") String namespace,
        @NotNull @DefaultValue("5m") Duration productTtl,
        @NotNull @DefaultValue("2m") Duration searchTtl,
        @NotNull @DefaultValue("10m") Duration categoryTtl) {

    @AssertTrue(message = "catalog cache TTL values must be positive")
    public boolean areTtlsValid() {
        return positive(productTtl) && positive(searchTtl) && positive(categoryTtl);
    }

    private boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
