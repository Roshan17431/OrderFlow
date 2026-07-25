package com.orderflow.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.refresh")
public record RefreshTokenProperties(
        @NotNull @DefaultValue("14d") Duration tokenTtl,
        @DefaultValue("32") int tokenBytes,
        @NotNull @DefaultValue("60s") Duration oauthExchangeTtl) {

    @AssertTrue(message = "refresh token TTL and OAuth exchange TTL must be positive")
    public boolean isDurationConfigurationValid() {
        return positive(tokenTtl) && positive(oauthExchangeTtl);
    }

    @AssertTrue(message = "refresh tokens must contain at least 256 bits of entropy")
    public boolean isTokenSizeValid() {
        return tokenBytes >= 32;
    }

    private boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }
}
