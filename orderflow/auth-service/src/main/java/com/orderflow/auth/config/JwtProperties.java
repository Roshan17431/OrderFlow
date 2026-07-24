package com.orderflow.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank @DefaultValue("https://orderflow.local") String issuer,
        @NotBlank @DefaultValue("orderflow-local") String keyId,
        @NotNull @DefaultValue("10m") Duration accessTokenTtl,
        @DefaultValue("false") boolean allowEphemeralKeys,
        String privateKeyLocation,
        String publicKeyLocation,
        @PositiveOrZero @DefaultValue("2048") int minimumKeySize) {

    @AssertTrue(message = "JWT access-token TTL must be positive")
    public boolean isAccessTokenTtlValid() {
        return accessTokenTtl != null && !accessTokenTtl.isNegative() && !accessTokenTtl.isZero();
    }

    @AssertTrue(message = "RSA keys are required unless ephemeral keys are explicitly allowed")
    public boolean isKeyConfigurationValid() {
        return allowEphemeralKeys
                || (hasText(privateKeyLocation) && hasText(publicKeyLocation));
    }

    @AssertTrue(message = "minimum RSA key size must be at least 2048 bits")
    public boolean isMinimumKeySizeValid() {
        return minimumKeySize >= 2048;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
