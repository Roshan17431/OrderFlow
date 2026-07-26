package com.orderflow.cart.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.clients.product")
public record ProductClientProperties(
        @NotBlank @DefaultValue("http://PRODUCT-SERVICE") String baseUrl,
        @NotNull @DefaultValue("2s") Duration connectTimeout,
        @NotNull @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("true") boolean discoveryEnabled) {

    @AssertTrue(message = "product-client timeouts must be positive")
    public boolean areTimeoutsValid() {
        return positive(connectTimeout) && positive(readTimeout);
    }

    private boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
