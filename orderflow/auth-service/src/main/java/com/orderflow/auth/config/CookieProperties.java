package com.orderflow.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.cookies")
public record CookieProperties(
        @NotBlank @DefaultValue("ORDERFLOW_REFRESH") String refreshCookieName,
        @DefaultValue("true") boolean secure,
        @NotBlank @DefaultValue("Strict") String sameSite,
        @NotBlank @DefaultValue("/api/auth") String path) {

    @AssertTrue(message = "cookie same-site must be Strict, Lax, or None")
    public boolean isSameSiteValid() {
        return "strict".equalsIgnoreCase(sameSite)
                || "lax".equalsIgnoreCase(sameSite)
                || "none".equalsIgnoreCase(sameSite);
    }

    @AssertTrue(message = "SameSite=None cookies must be Secure")
    public boolean isNoneCookieSecure() {
        return !"none".equalsIgnoreCase(sameSite) || secure;
    }
}
