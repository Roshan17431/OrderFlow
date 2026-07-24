package com.orderflow.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record GatewaySecurityProperties(
        @NotBlank @DefaultValue("https://orderflow.local") String issuer,
        @NotBlank @DefaultValue("http://localhost:8081/api/auth/.well-known/jwks.json")
                String jwkSetUri) {
}
