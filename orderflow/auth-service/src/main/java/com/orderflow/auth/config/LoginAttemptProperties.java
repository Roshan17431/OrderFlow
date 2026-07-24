package com.orderflow.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.login-attempts")
public record LoginAttemptProperties(
        @Positive @DefaultValue("5") int maxAttempts,
        @NotNull @DefaultValue("15m") Duration window) {

    @AssertTrue(message = "login-attempt window must be positive")
    public boolean isWindowValid() {
        return window != null && !window.isNegative() && !window.isZero();
    }
}
